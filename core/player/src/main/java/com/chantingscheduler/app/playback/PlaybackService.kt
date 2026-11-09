package com.chantingscheduler.app.playback

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.chantingscheduler.app.core.player.R
import com.chantingscheduler.app.data.AppDatabase
import com.chantingscheduler.app.data.PlaybackSettingsStore
import com.chantingscheduler.app.data.ScheduleRepository
import com.chantingscheduler.app.data.SessionEndReason
import com.chantingscheduler.app.data.SessionLogEntity
import com.chantingscheduler.app.data.SessionSource
import com.chantingscheduler.app.scheduling.AlarmScheduler
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Foreground media-session service that plays an ordered list of audio URIs
 * (a chanting session) with an optional gap between items, so playback
 * continues with the screen off and lock-screen / Bluetooth media controls
 * work. ExoPlayer has no native inter-track gap, so each automatic track
 * transition pauses the next track and resumes it after the configured delay.
 *
 * Two session shapes:
 * - **Playlist mode**: the playlist plays through, expanded to
 *   [repeatCount] passes. With an auto-stop duration the playlist loops and a
 *   5-second fade-out leads into a natural stop.
 * - **Routine mode**: ordered steps, each looping its sound (or holding a
 *   partial wake lock through silence) for its duration, advancing on
 *   handler timers.
 *
 * Publishes progress to [PlaybackState] for the UI.
 */
@SuppressLint("UnsafeOptInUsageError") // custom MediaNotification.Provider is an unstable Media3 API
class PlaybackService : MediaSessionService() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var gapMs: Long = 0
    private var trackCount: Int = 0
    private var label: String = ""
    private var scheduleId: Long? = null
    private var fadeDurationMs: Long = 0
    private var fadeStartUptimeMs: Long = 0
    private var sessionActive: Boolean = false
    private var sessionStartMs: Long = 0
    private var sessionSource: SessionSource = SessionSource.MANUAL
    private var routine: RoutinePlan? = null
    private var stepIndex: Int = 0
    private var silenceWakeLock: PowerManager.WakeLock? = null
    // Held through inter-track gaps: gap timers are uptime-based Handler posts,
    // which don't fire while the CPU sleeps. During a gap the player is paused,
    // so ExoPlayer's WAKE_MODE_LOCAL lock is released and the device can doze
    // indefinitely, stalling the session mid-gap.
    private var gapWakeLock: PowerManager.WakeLock? = null
    private val failurePolicy = PlaylistFailurePolicy()
    private var uniquePlaylistUris: Set<String> = emptySet()
    private var failedStepCount: Int = 0
    // Uptime of the pending auto-stop deadline, so the fallback chime can keep
    // whichever end-of-session limit comes first. 0 = no auto-stop posted.
    private var autoStopDeadlineUptimeMs: Long = 0
    // Chant loudness ceiling (0.25..1.0) read at session start; VolumeFade
    // ramps up to this instead of 1.0. Alarm-stream chants are often much
    // louder than media-stream ones, so playback is capped by default (70%).
    private var chantVolumeCeiling: Float = 1f
    private var playingFallback: Boolean = false
    private var alarmMutedNotified: Boolean = false
    // True once the player has rendered audio this session; gates the
    // audio-focus retry loop for alarm sessions.
    private var audioRendered: Boolean = false
    // True when the user deliberately paused (lock-screen/Bluetooth controls).
    // The audio-focus retry must never fight a user pause.
    private var userPaused: Boolean = false
    // Set around internal pause() calls (gap hold) so they aren't mistaken for
    // a user pause in onPlayWhenReadyChanged.
    private var internalPause: Boolean = false
    // Wall-clock base for the audio-focus retry window: session start for
    // playlists, the current audible routine step's start for routines. Never
    // a silence step — silence-first/silence-only routines must not be killed
    // by a window that started before any audio was expected.
    private var retryWindowStartMs: Long = 0
    // Original ACTION_PLAY intent, kept so the "missed chant" notification can re-deliver it.
    private var lastPlayIntent: Intent? = null
    private var gapActive: Boolean = false
    // Actual audio time, excluding inter-track gaps and silence (audioSeconds).
    private var audioAccumulatedMs: Long = 0
    private var playingSinceUptimeMs: Long = 0
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
            ) {
                return
            }
            publishState()
            val inRoutine = routine != null
            if (gapMs > 0 && !inRoutine) {
                // Pause the freshly started track, resume after the gap. The
                // notification explains the hold; pressing play explicitly
                // skips the remaining gap.
                handler.removeCallbacksAndMessages(GAP_TOKEN)
                gapActive = true
                internalPause = true
                player?.pause()
                internalPause = false
                acquireGapWakeLock(gapMs + GAP_WAKELOCK_EXTRA_MS)
                refreshNotification()
                handler.postAtTime(
                    {
                        gapActive = false
                        releaseGapWakeLock()
                        refreshNotification()
                        player?.play()
                    },
                    GAP_TOKEN,
                    SystemClock.uptimeMillis() + gapMs,
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                // In routine mode the playlist never ends on its own; silence
                // steps don't drive playback state at all.
                if (routine == null) stopPlayback(SessionEndReason.COMPLETED)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Accumulate wall-clock audio time while actually rendering audio.
            if (isPlaying) {
                audioRendered = true
                playingSinceUptimeMs = SystemClock.uptimeMillis()
            } else if (playingSinceUptimeMs > 0) {
                audioAccumulatedMs += SystemClock.uptimeMillis() - playingSinceUptimeMs
                playingSinceUptimeMs = 0
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) return
            if (!playWhenReady && !internalPause) {
                userPaused = true
            } else if (playWhenReady) {
                userPaused = false
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val p = player ?: return
            if (playingFallback) {
                // Even the bundled chime failed (shouldn't happen): end cleanly.
                stopPlayback(SessionEndReason.COMPLETED)
                return
            }
            if (routine != null) {
                // A broken step sound is skipped; the routine moves on. When
                // every playable step has failed, ring the fallback chime so
                // the routine doesn't run in silence.
                notifySoundsBroken()
                failedStepCount++
                val plan = routine
                if (plan != null && failedStepCount >= plan.playableStepCount) {
                    startFallbackChime()
                } else {
                    advanceStep()
                }
                return
            }
            val failedUri = p.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
            when (failurePolicy.onTrackFailed(failedUri, uniquePlaylistUris)) {
                PlaylistFailurePolicy.Decision.Fallback -> startFallbackChime()
                PlaylistFailurePolicy.Decision.Skip -> {
                    // Remove the broken item so it isn't retried on every loop
                    // pass; the policy decides where playback continues.
                    val index = p.currentMediaItemIndex
                    p.removeMediaItem(index)
                    trackCount = p.mediaItemCount
                    val next = failurePolicy.nextIndexAfterRemoval(
                        removedIndex = index,
                        remainingCount = p.mediaItemCount,
                        loops = p.repeatMode == Player.REPEAT_MODE_ALL,
                    )
                    when (next) {
                        is PlaylistFailurePolicy.NextIndex.Seek -> {
                            p.seekTo(next.index, C.TIME_UNSET)
                            p.prepare()
                            p.play()
                        }
                        PlaylistFailurePolicy.NextIndex.Complete -> {
                            // All-failed single track never reaches here (the
                            // decision is Fallback); this is a one-shot
                            // playlist whose broken item was last.
                            stopPlayback(SessionEndReason.COMPLETED)
                        }
                    }
                }
            }
        }
    }

    private val fadeStep = object : Runnable {
        override fun run() {
            val p = player ?: return
            val elapsed = SystemClock.uptimeMillis() - fadeStartUptimeMs
            p.volume = VolumeFade.volumeAt(elapsed, fadeDurationMs) * chantVolumeCeiling
            if (elapsed < fadeDurationMs) {
                handler.postAtTime(this, FADE_TOKEN, SystemClock.uptimeMillis() + FADE_STEP_MS)
            }
        }
    }

    private val fadeOutStep = object : Runnable {
        override fun run() {
            val p = player ?: return
            val elapsed = SystemClock.uptimeMillis() - fadeStartUptimeMs
            // ceiling -> 0 over AUTO_STOP_FADE_MS; the final stop happens via the
            // AUTO_STOP_TOKEN posted for fade end.
            p.volume = chantVolumeCeiling *
                (1f - VolumeFade.volumeAt(elapsed, AUTO_STOP_FADE_MS).coerceAtMost(1f))
            if (elapsed < AUTO_STOP_FADE_MS) {
                handler.postAtTime(this, FADE_OUT_TOKEN, SystemClock.uptimeMillis() + FADE_STEP_MS)
            }
        }
    }

    private val stepAdvance = object : Runnable {
        override fun run() {
            advanceStep()
        }
    }

    /**
     * Alarm sessions whose audio focus was denied (e.g. during a phone call)
     * would otherwise hang silently while the session timers run. Re-issue
     * play() per [AudioFocusRetryPolicy]; after the grace window, give up with
     * a "missed chant" notification the user can tap to play.
     */
    private val audioFocusRetry = object : Runnable {
        override fun run() {
            val p = player ?: return
            if (!sessionActive || audioRendered || userPaused) return
            val elapsed = System.currentTimeMillis() - retryWindowStartMs
            when (
                AudioFocusRetryPolicy(
                    isAlarmSession = sessionSource == SessionSource.ALARM,
                    startElapsedMs = elapsed,
                    hasRenderedAudio = audioRendered,
                    userPaused = userPaused,
                ).action()
            ) {
                AudioFocusRetryPolicy.Action.Retry -> {
                    p.play()
                    handler.postAtTime(
                        this,
                        FOCUS_RETRY_TOKEN,
                        SystemClock.uptimeMillis() + AudioFocusRetryPolicy.RETRY_INTERVAL_MS,
                    )
                }
                AudioFocusRetryPolicy.Action.GiveUp -> {
                    notifyMissedChant()
                    stopPlayback(SessionEndReason.STOPPED)
                }
                AudioFocusRetryPolicy.Action.None ->
                    handler.postAtTime(
                        this,
                        FOCUS_RETRY_TOKEN,
                        SystemClock.uptimeMillis() + AudioFocusRetryPolicy.RETRY_INTERVAL_MS,
                    )
            }
        }
    }

    private val autoStop = Runnable {
        // 5s fade-out into a natural completion.
        handler.removeCallbacksAndMessages(FADE_OUT_TOKEN)
        fadeStartUptimeMs = SystemClock.uptimeMillis()
        handler.postAtTime(fadeOutStep, FADE_OUT_TOKEN, SystemClock.uptimeMillis() + FADE_STEP_MS)
        handler.postAtTime(
            { stopPlayback(SessionEndReason.COMPLETED) },
            AUTO_STOP_TOKEN,
            SystemClock.uptimeMillis() + AUTO_STOP_FADE_MS,
        )
    }

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exoPlayer.addListener(playerListener)
        player = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(openIntent())
            .build()
        setMediaNotificationProvider(PlaybackNotificationProvider())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopPlayback(SessionEndReason.STOPPED)
            ACTION_SNOOZE -> snooze()
            ACTION_PLAY -> startPlayback(intent)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startPlayback(intent: Intent) {
        val uris = intent.getStringArrayListExtra(EXTRA_URIS).orEmpty()
        val stepPlan = RoutinePlan.fromExtras(
            uris = intent.getStringArrayListExtra(EXTRA_STEP_URIS).orEmpty(),
            minutes = intent.getIntArrayExtra(EXTRA_STEP_MINUTES)?.toList().orEmpty(),
        )
        if (uris.isEmpty() && !stepPlan.isRoutine) {
            stopPlayback(SessionEndReason.STOPPED)
            return
        }
        // Replacing an active session counts as stopping it.
        logSession(SessionEndReason.STOPPED)
        routine = stepPlan.takeIf { it.isRoutine }
        stepIndex = 0
        gapMs = if (routine != null) 0 else intent.getIntExtra(EXTRA_GAP_SECONDS, 0) * 1000L
        label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
            .ifBlank { getString(R.string.app_name) }
        scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L).takeIf { it >= 0 }
        sessionSource = intent.getStringExtra(EXTRA_SOURCE)
            ?.let { runCatching { SessionSource.valueOf(it) }.getOrNull() }
            ?: SessionSource.MANUAL
        val playbackSettings = PlaybackSettingsStore.get(this)
        chantVolumeCeiling = playbackSettings.currentChantVolumePercent / 100f
        fadeDurationMs = intent.getIntExtra(EXTRA_FADE_IN_SECONDS, 0) * 1000L
        // Alarm-stream chants startle at full alarm volume when fade-in is off;
        // a short default fade softens the start (only for alarm sessions on
        // the alarm stream — manual sessions respect the exact setting).
        if (sessionSource == SessionSource.ALARM &&
            playbackSettings.currentAlarmVolumeEnabled &&
            fadeDurationMs == 0L
        ) {
            fadeDurationMs = DEFAULT_ALARM_FADE_IN_MS
        }
        val repeatCount = intent.getIntExtra(EXTRA_REPEAT_COUNT, 1).coerceAtLeast(1)
        val maxDurationMinutes = intent.getIntExtra(EXTRA_MAX_DURATION_MINUTES, -1)
        sessionStartMs = System.currentTimeMillis()
        failurePolicy.reset()
        failedStepCount = 0
        playingFallback = false
        audioAccumulatedMs = 0
        playingSinceUptimeMs = 0
        alarmMutedNotified = false
        audioRendered = false
        userPaused = false
        retryWindowStartMs = sessionStartMs
        lastPlayIntent = intent
        // A real session takes over the audio focus; stop any edit-screen preview.
        SoundPreviewPlayer.stop()
        // A fresh session supersedes any pending snooze for this schedule.
        scheduleId?.let { AlarmScheduler(this).cancelSnooze(it) }
        handler.removeCallbacksAndMessages(GAP_TOKEN)
        handler.removeCallbacksAndMessages(STEP_TOKEN)
        handler.removeCallbacksAndMessages(AUTO_STOP_TOKEN)
        handler.removeCallbacksAndMessages(FADE_OUT_TOKEN)
        handler.removeCallbacksAndMessages(FALLBACK_TOKEN)
        handler.removeCallbacksAndMessages(FOCUS_RETRY_TOKEN)
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_MISSED) }
        gapActive = false
        releaseGapWakeLock()
        sessionActive = true
        startForegroundWithNotification()
        WidgetBridge.requestUpdate(this)
        val p = player ?: return
        applySessionAudioAttributes(p)
        if (routine != null) {
            // Routines arm the retry per audible step in startStep — a
            // silence-first routine must not inherit the session-start window.
            startStep(0)
            return
        }
        if (sessionSource == SessionSource.ALARM) {
            handler.postAtTime(
                audioFocusRetry,
                FOCUS_RETRY_TOKEN,
                SystemClock.uptimeMillis() + AudioFocusRetryPolicy.FIRST_RETRY_MS,
            )
        }
        // Playlist mode: expand to the requested number of passes so a
        // multi-pass session ends naturally after the last pass.
        uniquePlaylistUris = uris.toSet()
        val expanded = if (repeatCount > 1) {
            List(repeatCount) { uris }.flatten()
        } else {
            uris
        }
        trackCount = expanded.size
        p.repeatMode = if (maxDurationMinutes > 0) {
            Player.REPEAT_MODE_ALL // loop until the auto-stop fade cuts in
        } else {
            Player.REPEAT_MODE_OFF
        }
        p.setMediaItems(expanded.map { MediaItem.fromUri(it) })
        if (maxDurationMinutes > 0) {
            val deadline = sessionStartMs + maxDurationMinutes * 60_000L
            val fadeStart = (deadline - AUTO_STOP_FADE_MS - System.currentTimeMillis())
                .coerceAtLeast(0)
            autoStopDeadlineUptimeMs = SystemClock.uptimeMillis() + fadeStart + AUTO_STOP_FADE_MS
            handler.postAtTime(autoStop, AUTO_STOP_TOKEN, SystemClock.uptimeMillis() + fadeStart)
        } else {
            autoStopDeadlineUptimeMs = 0
        }
        startFadeIfNeeded()
        p.prepare()
        p.play()
        publishState()
    }

    /**
     * Alarm-sourced sessions render on the alarm audio stream when the setting
     * is on, so a chant still sounds at media volume 0 or under DND. ExoPlayer
     * fixes the audio stream at prepare() time, so this must run before any
     * prepare/play for the session. Manual sessions keep the media stream.
     */
    private fun applySessionAudioAttributes(p: ExoPlayer) {
        val useAlarmVolume = sessionSource == SessionSource.ALARM &&
            PlaybackSettingsStore.get(this).currentAlarmVolumeEnabled
        // handleAudioFocus is only supported for USAGE_MEDIA/USAGE_GAME;
        // passing true with USAGE_ALARM throws asynchronously (surfaced as
        // ERROR_CODE_FAILED_RUNTIME_CHECK), which used to kill every
        // alarm-sourced routine via the failure path. The alarm stream
        // doesn't duck other audio, so no focus handling is needed there.
        p.setAudioAttributes(
            if (useAlarmVolume) ALARM_AUDIO_ATTRIBUTES else AudioAttributes.DEFAULT,
            /* handleAudioFocus= */ !useAlarmVolume,
        )
        if (useAlarmVolume) maybeNotifyAlarmVolumeMuted()
    }

    /** One-time heads-up when the alarm stream is muted, so a "silent alarm" is never a surprise. */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() guards the post
    private fun maybeNotifyAlarmVolumeMuted() {
        if (alarmMutedNotified) return
        val audioManager = getSystemService(AudioManager::class.java) ?: return
        if (audioManager.getStreamVolume(AudioManager.STREAM_ALARM) > 0) return
        alarmMutedNotified = true
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val openApp = PendingIntent.getActivity(
            this,
            5,
            Intent()
                .setClassName(packageName, "com.chantingscheduler.app.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_FALLBACK)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.alarm_volume_muted_title))
            .setContentText(getString(R.string.alarm_volume_muted_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_MUTED, notification)
        }
    }

    /**
     * Posted when an alarm session gives up waiting for audio focus: tells the
     * user which chant was missed and re-delivers the session on tap.
     */
    @SuppressLint("MissingPermission") // areNotificationsEnabled() guards the post
    private fun notifyMissedChant() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val play = lastPlayIntent
        val contentIntent = if (play != null) {
            PendingIntent.getForegroundService(
                this,
                6,
                play,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getActivity(
                this,
                6,
                Intent()
                    .setClassName(packageName, "com.chantingscheduler.app.MainActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val time = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(sessionStartMs))
        val notification = NotificationCompat.Builder(this, CHANNEL_FALLBACK)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.missed_chant_title, time))
            .setContentText(getString(R.string.missed_chant_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_MISSED, notification)
        }
    }

    // region Routine mode

    private fun startStep(index: Int) {
        val plan = routine ?: return
        stepIndex = index
        // A broken step may have been skipped by onPlayerError while its own
        // stepAdvance timer was still posted — clear it so a stale timer can't
        // fire mid-step and cut the replacement step short.
        handler.removeCallbacksAndMessages(STEP_TOKEN)
        releaseSilenceWakeLock()
        publishState()
        refreshNotification()
        if (plan.isSilence(index)) {
            // No audio is expected during silence: the focus retry must not
            // run (and never on a silence-only routine).
            handler.removeCallbacksAndMessages(FOCUS_RETRY_TOKEN)
            // Hold the CPU through the silence so the timer can't be deferred.
            val timeoutMs = plan.durationMillis(index) + SILENCE_WAKELOCK_EXTRA_MS
            silenceWakeLock = getSystemService(PowerManager::class.java)
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chanting:silence")
                ?.apply { runCatching { acquire(timeoutMs) } }
            handler.postAtTime(
                stepAdvance, STEP_TOKEN,
                SystemClock.uptimeMillis() + plan.durationMillis(index),
            )
        } else {
            val p = player ?: return
            p.repeatMode = Player.REPEAT_MODE_ONE
            p.setMediaItems(listOf(MediaItem.fromUri(plan.soundUri(index)!!)))
            startFadeIfNeeded()
            p.prepare()
            p.play()
            handler.postAtTime(
                stepAdvance, STEP_TOKEN,
                SystemClock.uptimeMillis() + plan.durationMillis(index),
            )
            // Arm the focus retry for this audible step, window starting now.
            retryWindowStartMs = System.currentTimeMillis()
            if (sessionSource == SessionSource.ALARM && !userPaused) {
                handler.removeCallbacksAndMessages(FOCUS_RETRY_TOKEN)
                handler.postAtTime(
                    audioFocusRetry,
                    FOCUS_RETRY_TOKEN,
                    SystemClock.uptimeMillis() + AudioFocusRetryPolicy.FIRST_RETRY_MS,
                )
            }
        }
    }

    private fun advanceStep() {
        val plan = routine ?: return
        val next = plan.indexAfter(stepIndex)
        if (next < 0) {
            stopPlayback(SessionEndReason.COMPLETED)
        } else {
            startStep(next)
        }
    }

    private fun releaseSilenceWakeLock() {
        silenceWakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        silenceWakeLock = null
    }

    private fun acquireGapWakeLock(timeoutMs: Long) {
        releaseGapWakeLock()
        gapWakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "chanting:gap")
            ?.apply { runCatching { acquire(timeoutMs) } }
    }

    private fun releaseGapWakeLock() {
        gapWakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        gapWakeLock = null
    }

    // endregion

    private fun startFadeIfNeeded() {
        handler.removeCallbacksAndMessages(FADE_TOKEN)
        val p = player ?: return
        if (fadeDurationMs <= 0L) {
            p.volume = chantVolumeCeiling
            return
        }
        fadeStartUptimeMs = SystemClock.uptimeMillis()
        p.volume = 0f
        handler.postAtTime(fadeStep, FADE_TOKEN, SystemClock.uptimeMillis() + FADE_STEP_MS)
    }

    /**
     * Every playlist URI failed: loop the bundled bell chime so the alarm is
     * still audible, tell the user their schedule needs repair, and surface a
     * "no audio played" explanation in the log via the normal COMPLETED path.
     */
    private fun startFallbackChime() {
        playingFallback = true
        notifySoundsBroken()
        trackCount = 1
        val p = player ?: return
        handler.removeCallbacksAndMessages(GAP_TOKEN)
        // The chime is a last resort, not an infinite bell: cap it at
        // FALLBACK_MAX_MS, or at the schedule's auto-stop deadline when one is
        // already armed, whichever comes first.
        val fallbackDeadline = SystemClock.uptimeMillis() + FALLBACK_MAX_MS
        val deadline = if (autoStopDeadlineUptimeMs > 0) {
            minOf(fallbackDeadline, autoStopDeadlineUptimeMs)
        } else {
            fallbackDeadline
        }
        handler.postAtTime(
            { stopPlayback(SessionEndReason.COMPLETED) },
            FALLBACK_TOKEN,
            deadline,
        )
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.setMediaItems(
            listOf(
                MediaItem.fromUri(
                    "android.resource://$packageName/${R.raw.fallback_chime}",
                ),
            ),
        )
        p.prepare()
        p.play()
        publishState()
    }

    private fun notifySoundsBroken() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        val openApp = PendingIntent.getActivity(
            this,
            4,
            Intent()
                .setClassName(packageName, "com.chantingscheduler.app.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_FALLBACK)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.sounds_broken_title))
            .setContentText(getString(R.string.sounds_broken_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID_BROKEN, notification)
        }
    }

    private fun publishState() {
        val p = player ?: return
        val plan = routine
        if (plan != null) {
            PlaybackState.update(
                label = label,
                currentTrack = 0,
                totalTracks = 0,
                scheduleId = scheduleId,
                currentStep = stepIndex + 1,
                totalSteps = plan.stepCount,
            )
        } else {
            PlaybackState.update(label, p.currentMediaItemIndex + 1, trackCount, scheduleId)
        }
    }

    /** Stops playback and re-rings the same playlist a few minutes later. */
    private fun snooze() {
        val id = scheduleId
        if (id != null) {
            val minutes = PlaybackSettingsStore.get(this).currentSnoozeMinutes
            AlarmScheduler(this).scheduleSnooze(id, System.currentTimeMillis() + minutes * 60_000L)
        }
        stopPlayback(SessionEndReason.SNOOZED)
    }

    private fun stopPlayback(endReason: SessionEndReason) {
        logSession(endReason)
        sessionActive = false
        handler.removeCallbacksAndMessages(null)
        player?.let {
            it.volume = 1f
            it.stop()
            it.clearMediaItems()
            it.repeatMode = Player.REPEAT_MODE_OFF
        }
        routine = null
        releaseSilenceWakeLock()
        releaseGapWakeLock()
        PlaybackState.clear()
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_BROKEN) }
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_MUTED) }
        runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_MISSED) }
        WidgetBridge.requestUpdate(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Records the finished session for history/stats. The service is not
     * Hilt-injected, so it uses the codebase's existing singleton pattern
     * (`AppDatabase.get`); preview playback never reaches this (it lives in
     * [SoundPreviewPlayer]). [SessionLogEntity.audioSeconds] counts actual
     * audio rendering time, excluding gaps and silence.
     */
    private fun logSession(endReason: SessionEndReason) {
        val startedAt = sessionStartMs
        if (!sessionActive || startedAt <= 0L) return
        if (playingSinceUptimeMs > 0) {
            audioAccumulatedMs += SystemClock.uptimeMillis() - playingSinceUptimeMs
            playingSinceUptimeMs = 0
        }
        val log = SessionLogEntity(
            scheduleId = scheduleId,
            startedAtEpochMs = startedAt,
            durationSeconds = ((System.currentTimeMillis() - startedAt) / 1000L)
                .toInt().coerceAtLeast(0),
            endReason = endReason,
            source = sessionSource,
            audioSeconds = (audioAccumulatedMs / 1000L).toInt(),
        )
        serviceScope.launch {
            runCatching { ScheduleRepository(AppDatabase.get(applicationContext)).logSession(log) }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep an active session alive when the app is swiped away; otherwise
        // release the service like the default MediaSessionService behavior.
        if (!sessionActive) stopSelf()
    }

    override fun onUpdateNotification(session: MediaSession, startInForeground: Boolean) {
        // While tearing down, don't let the session resurrect the notification.
        if (!sessionActive) return
        super.onUpdateNotification(session, startInForeground)
    }

    override fun onDestroy() {
        sessionActive = false
        handler.removeCallbacksAndMessages(null)
        releaseSilenceWakeLock()
        releaseGapWakeLock()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
        }
        mediaSession = null
        player = null
        PlaybackState.clear()
        super.onDestroy()
        runCatching {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_BROKEN)
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_MUTED)
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID_MISSED)
        }
    }

    // region Notification

    private fun openIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        1,
        // RingingActivity lives in the app module (downstream of this library),
        // so it is referenced by class name.
        Intent()
            .setClassName(packageName, RINGING_ACTIVITY_CLASS)
            .putExtra(EXTRA_RINGING_SCHEDULE_ID, scheduleId ?: -1L)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun statusText(): String {
        val plan = routine
        return when {
            plan != null -> getString(R.string.routine_step_status, stepIndex + 1, plan.stepCount)
            gapActive -> getString(R.string.gap_pausing)
            else -> getString(R.string.playing)
        }
    }

    /**
     * Builds the session notification. Alarm-sourced sessions live on the
     * high-importance [CHANNEL_ALARM] so the full-screen intent (locked-screen
     * ringing page) and heads-up are honored; manual sessions stay on the
     * quiet [CHANNEL_PLAYBACK] channel. [fullScreenIntent] is attached only to
     * the *initial* alarm post — every later re-post must not re-trigger it.
     */
    private fun buildNotification(fullScreenIntent: Boolean): Notification {
        val openIntent = openIntent()
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozeIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, PlaybackService::class.java).setAction(ACTION_SNOOZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1)
        mediaSession?.let {
            mediaStyle.setMediaSession(MediaSessionCompat.Token.fromToken(it.platformToken))
        }
        val isAlarm = sessionSource == SessionSource.ALARM
        val builder = NotificationCompat.Builder(
            this,
            if (isAlarm) CHANNEL_ALARM else CHANNEL_PLAYBACK,
        )
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(label)
            .setContentText(statusText())
            .setOngoing(true)
            .setCategory(
                if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_TRANSPORT,
            )
            .setContentIntent(openIntent)
            .setStyle(mediaStyle)
            .addAction(R.drawable.ic_snooze, getString(R.string.snooze), snoozeIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.stop), stopIntent)
        if (fullScreenIntent && isAlarm) {
            builder.setFullScreenIntent(openIntent, true)
        } else {
            // Re-posts (gap/step updates, Media3 provider refreshes) must not
            // re-launch the ringing screen or re-alert.
            builder.setOnlyAlertOnce(true)
        }
        return builder.build()
    }

    private fun startForegroundWithNotification() {
        val fullScreen = sessionSource == SessionSource.ALARM
        if (fullScreen) {
            // Drop any previous ongoing post first: the system only launches
            // the full-screen intent when the notification is NEWLY posted.
            // Re-posting over an existing id can suppress the locked-screen
            // ringing page (observed as an occasional missed FSI launch).
            runCatching { NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID) }
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(fullScreenIntent = fullScreen),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    /** Rebuilds the posted notification in place (routine step advances). */
    @SuppressLint("MissingPermission") // session start already gated by the caller
    private fun refreshNotification() {
        if (!sessionActive) return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(fullScreenIntent = false))
        }
    }

    /**
     * Serves the session's media notification. Rebuilds the app's own
     * notification (same channel and Stop + Snooze actions) instead of the
     * generic Media3 layout, so existing behavior is preserved. Never
     * re-attaches the full-screen intent — that fires only on the initial
     * alarm-sourced post.
     */
    private inner class PlaybackNotificationProvider : MediaNotification.Provider {
        override fun createNotification(
            mediaSession: MediaSession,
            customLayout: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback,
        ): MediaNotification = MediaNotification(NOTIFICATION_ID, buildNotification(fullScreenIntent = false))

        override fun handleCustomCommand(
            session: MediaSession,
            action: String,
            extras: Bundle,
        ): Boolean = false
    }

    // endregion

    companion object {
        /** Notification channel id; the channel itself is created by the app module. */
        const val CHANNEL_PLAYBACK = "playback"
        /**
         * High-importance channel for the initial notification of alarm-sourced
         * sessions: below [android.app.NotificationManager.IMPORTANCE_HIGH]
         * Android suppresses full-screen intents and heads-up, so the ringing
         * page would never appear on a locked screen. No sound/vibration —
         * the audio comes from ExoPlayer. Created by the app module.
         */
        const val CHANNEL_ALARM = "chant_alarm"
        /** High-priority channel for alarm-time notifications that can't start the FGS directly. */
        const val CHANNEL_FALLBACK = "playback_fallback"
        const val ACTION_PLAY = "com.chantingscheduler.app.action.PLAY"
        const val ACTION_STOP = "com.chantingscheduler.app.action.STOP"
        const val ACTION_SNOOZE = "com.chantingscheduler.app.action.SNOOZE"
        /** Alarm-stream attributes for alarm-sourced sessions (setting on). */
        private val ALARM_AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        const val EXTRA_URIS = "uris"
        const val EXTRA_GAP_SECONDS = "gap_seconds"
        const val EXTRA_LABEL = "label"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_FADE_IN_SECONDS = "fade_in_seconds"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_STEP_URIS = "step_uris"
        const val EXTRA_STEP_MINUTES = "step_minutes"
        const val EXTRA_REPEAT_COUNT = "repeat_count"
        const val EXTRA_MAX_DURATION_MINUTES = "max_duration_minutes"

        // Must match RingingActivity.EXTRA_SCHEDULE_ID in the app module.
        private const val EXTRA_RINGING_SCHEDULE_ID = "schedule_id"
        private const val RINGING_ACTIVITY_CLASS = "com.chantingscheduler.app.RingingActivity"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_BROKEN = 1002
        private const val NOTIFICATION_ID_BLOCKED = 1003
        private const val NOTIFICATION_ID_MUTED = 1004
        private const val NOTIFICATION_ID_MISSED = 1005
        private const val FADE_STEP_MS = 50L
        private const val AUTO_STOP_FADE_MS = 5_000L
        private const val DEFAULT_ALARM_FADE_IN_MS = 15_000L
        private const val SILENCE_WAKELOCK_EXTRA_MS = 60_000L
        private const val GAP_WAKELOCK_EXTRA_MS = 30_000L
        private const val FALLBACK_MAX_MS = 120_000L
        private val GAP_TOKEN = Any()
        private val FADE_TOKEN = Any()
        private val FADE_OUT_TOKEN = Any()
        private val STEP_TOKEN = Any()
        private val AUTO_STOP_TOKEN = Any()
        private val FALLBACK_TOKEN = Any()
        private val FOCUS_RETRY_TOKEN = Any()

        fun start(
            context: Context,
            uris: List<String>,
            gapSeconds: Int,
            label: String,
            scheduleId: Long? = null,
            fadeInSeconds: Int = 0,
            source: SessionSource = SessionSource.MANUAL,
            stepUris: List<String> = emptyList(),
            stepMinutes: List<Int> = emptyList(),
            repeatCount: Int = 1,
            maxDurationMinutes: Int = -1,
        ) {
            ContextCompat.startForegroundService(
                context,
                playIntent(
                    context, uris, gapSeconds, label, scheduleId, fadeInSeconds, source,
                    stepUris, stepMinutes, repeatCount, maxDurationMinutes,
                ),
            )
        }

        /** Builds the ACTION_PLAY intent; exposed so guarded callers (e.g. the
         * alarm receiver's fallback notification) can re-deliver it on tap. */
        fun playIntent(
            context: Context,
            uris: List<String>,
            gapSeconds: Int,
            label: String,
            scheduleId: Long? = null,
            fadeInSeconds: Int = 0,
            source: SessionSource = SessionSource.MANUAL,
            stepUris: List<String> = emptyList(),
            stepMinutes: List<Int> = emptyList(),
            repeatCount: Int = 1,
            maxDurationMinutes: Int = -1,
        ): Intent = Intent(context, PlaybackService::class.java)
            .setAction(ACTION_PLAY)
            .putStringArrayListExtra(EXTRA_URIS, ArrayList(uris))
            .putExtra(EXTRA_GAP_SECONDS, gapSeconds)
            .putExtra(EXTRA_LABEL, label)
            .putExtra(EXTRA_SCHEDULE_ID, scheduleId ?: -1L)
            .putExtra(EXTRA_FADE_IN_SECONDS, fadeInSeconds)
            .putExtra(EXTRA_SOURCE, source.name)
            .putStringArrayListExtra(EXTRA_STEP_URIS, ArrayList(stepUris))
            .putExtra(EXTRA_STEP_MINUTES, stepMinutes.toIntArray())
            .putExtra(EXTRA_REPEAT_COUNT, repeatCount)
            .putExtra(EXTRA_MAX_DURATION_MINUTES, maxDurationMinutes)

        /**
         * Starts the service from contexts that may be in the background
         * (QS tile, home-screen widget). Android 12+ can deny the foreground
         * service start there, which would crash the caller with
         * ForegroundServiceStartNotAllowedException; on denial, fall back to
         * the tap-to-start notification so the session is never silently lost.
         * Returns true when the service actually started.
         */
        fun tryStart(context: Context, playIntent: Intent): Boolean = try {
            ContextCompat.startForegroundService(context, playIntent)
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (API 31+) is the
            // expected case; lint can't see through the version gate, so the
            // API-31 superclass is caught and re-verified here.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw e
            notifyStartBlocked(
                context,
                playIntent,
                playIntent.getStringExtra(EXTRA_LABEL).orEmpty(),
            )
            false
        }

        /**
         * Last-resort path when the system refuses a background foreground-service
         * start (Android 12+ restrictions): post a high-priority notification so
         * the alarm is never silently lost — tapping it starts the session.
         */
        fun notifyStartBlocked(context: Context, playIntent: Intent, label: String) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            // The target must go foreground (the FGS start was the thing that
            // got denied), so the tap has to use getForegroundService — a plain
            // getService PendingIntent would re-deliver in the background and be
            // refused again.
            val startPending = PendingIntent.getForegroundService(
                context,
                3,
                playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_FALLBACK)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(context.getString(R.string.time_to_chant_title))
                .setContentText(label.ifBlank { context.getString(R.string.time_to_chant_text) })
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(startPending)
                .build()
            runCatching {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BLOCKED, notification)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, PlaybackService::class.java).setAction(ACTION_STOP),
            )
        }

        fun snooze(context: Context) {
            context.startService(
                Intent(context, PlaybackService::class.java).setAction(ACTION_SNOOZE),
            )
        }
    }
}
