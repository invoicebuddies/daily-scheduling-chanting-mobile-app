package com.chantingscheduler.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot of what [PlaybackService] is currently doing, observable from the UI. */
data class NowPlaying(
    val isPlaying: Boolean = false,
    val label: String = "",
    val currentTrack: Int = 0,
    val totalTracks: Int = 0,
    val scheduleId: Long? = null,
    val startedAtMillis: Long = 0L,
    /** 1-based routine step position; 0/0 when running in playlist mode. */
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
    /** Wall-clock deadline of the current routine step; 0 = not applicable. */
    val stepEndsAtMillis: Long = 0L,
    /** Wall-clock auto-stop deadline; 0 = no auto-stop armed. */
    val autoStopAtMillis: Long = 0L,
) {
    fun isPlayingSchedule(id: Long): Boolean = isPlaying && scheduleId == id
}

/**
 * A session that just ended by itself (playlist/routine completion, auto-stop,
 * or a permanent focus loss) — published so the ringing page can close calmly
 * and offer the practice-journal note. Never published for user Stop/Snooze.
 */
data class EndedSession(
    val scheduleId: Long?,
    /** Row id of the session log, for attaching the journal note; null when the insert failed. */
    val logId: Long?,
    val label: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
)

/**
 * Process-wide playback state. The service and UI live in the same process, so a
 * singleton StateFlow is the simplest reliable observation channel (no binding or
 * broadcasts needed).
 */
object PlaybackState {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val _endedSession = MutableStateFlow<EndedSession?>(null)
    val endedSession: StateFlow<EndedSession?> = _endedSession.asStateFlow()

    fun update(
        label: String,
        currentTrack: Int,
        totalTracks: Int,
        scheduleId: Long?,
        currentStep: Int = 0,
        totalSteps: Int = 0,
        stepEndsAtMillis: Long = 0L,
        autoStopAtMillis: Long = 0L,
    ) {
        val previous = _state.value
        _state.value = NowPlaying(
            isPlaying = true,
            label = label,
            currentTrack = currentTrack,
            totalTracks = totalTracks,
            scheduleId = scheduleId,
            // Keep the original start time across track advances.
            startedAtMillis = if (previous.isPlaying && previous.scheduleId == scheduleId &&
                previous.label == label
            ) {
                previous.startedAtMillis
            } else {
                System.currentTimeMillis()
            },
            currentStep = currentStep,
            totalSteps = totalSteps,
            stepEndsAtMillis = stepEndsAtMillis,
            autoStopAtMillis = autoStopAtMillis,
        )
        // A live session supersedes any completed one on the ringing page.
        _endedSession.value = null
    }

    fun publishEnded(session: EndedSession) {
        _endedSession.value = session
    }

    /** Consumed by the ringing page once it has shown the complete state. */
    fun clearEnded() {
        _endedSession.value = null
    }

    fun clear() {
        _state.value = NowPlaying()
        _endedSession.value = null
    }
}
