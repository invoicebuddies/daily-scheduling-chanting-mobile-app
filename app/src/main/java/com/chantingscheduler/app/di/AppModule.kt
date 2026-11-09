package com.chantingscheduler.app.di

import android.content.Context
import androidx.core.content.ContextCompat
import com.chantingscheduler.app.data.PlaybackSettingsStore
import com.chantingscheduler.app.data.ScheduleWithSounds
import com.chantingscheduler.app.data.SessionSource
import com.chantingscheduler.app.playback.PlaybackService
import com.chantingscheduler.app.scheduling.SessionStarter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Starts a chanting session through the player module's foreground service. */
class PlaybackSessionStarter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackSettingsStore: PlaybackSettingsStore,
) : SessionStarter {
    override fun start(item: ScheduleWithSounds) {
        val playIntent = PlaybackService.playIntent(
            context = context,
            uris = item.sortedSounds.map { it.uri },
            gapSeconds = item.schedule.gapSeconds,
            label = item.schedule.label,
            scheduleId = item.schedule.id,
            fadeInSeconds = playbackSettingsStore.currentFadeInSeconds,
            source = SessionSource.ALARM,
            stepUris = item.sortedSteps.map { it.soundUri.orEmpty() },
            stepMinutes = item.sortedSteps.map { it.durationMinutes },
            repeatCount = item.schedule.repeatCount,
            maxDurationMinutes = item.schedule.maxDurationMinutes ?: -1,
        )
        try {
            ContextCompat.startForegroundService(context, playIntent)
        } catch (e: RuntimeException) {
            // Android 12+ can deny background FGS starts (especially on the
            // inexact-alarm fallback path). Never drop the alarm: post a
            // high-priority notification whose tap starts the session.
            PlaybackService.notifyStartBlocked(
                context,
                playIntent,
                item.schedule.label,
            )
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindSessionStarter(impl: PlaybackSessionStarter): SessionStarter
}
