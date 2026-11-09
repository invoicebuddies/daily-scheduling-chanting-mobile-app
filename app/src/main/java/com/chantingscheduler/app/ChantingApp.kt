package com.chantingscheduler.app

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import com.chantingscheduler.app.data.AppDatabase
import com.chantingscheduler.app.data.ScheduleRepository
import com.chantingscheduler.app.playback.PlaybackService
import com.chantingscheduler.app.scheduling.AlarmScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class ChantingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            PlaybackService.CHANNEL_PLAYBACK,
            getString(R.string.channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val fallbackChannel = NotificationChannel(
            PlaybackService.CHANNEL_FALLBACK,
            getString(com.chantingscheduler.app.core.player.R.string.channel_fallback),
            NotificationManager.IMPORTANCE_HIGH,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(fallbackChannel)
        // Alarm-sourced sessions: IMPORTANCE_HIGH is required for the
        // full-screen intent (locked-screen ringing page) to fire at all.
        // Deliberately a NEW channel id — an existing channel's importance
        // never updates in place. Silent: the chant audio is ExoPlayer's.
        val alarmChannel = NotificationChannel(
            PlaybackService.CHANNEL_ALARM,
            getString(com.chantingscheduler.app.core.player.R.string.channel_alarm),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(alarmChannel)
        val reminderChannel = NotificationChannel(
            com.chantingscheduler.app.scheduling.ReminderReceiver.CHANNEL_REMINDERS,
            getString(com.chantingscheduler.app.core.scheduling.R.string.reminder_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(reminderChannel)
        // Force-stop clears all alarms; re-arming on app start makes the next
        // launch self-healing. Idempotent: scheduling replaces PendingIntents.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { rearmAll() }
        }
    }

    private fun rearmAll() {
        val scheduler = AlarmScheduler(this)
        val repository = ScheduleRepository(AppDatabase.get(this))
        kotlinx.coroutines.runBlocking {
            scheduler.rearmAll(repository.getEnabled().map { it.schedule })
        }
    }
}
