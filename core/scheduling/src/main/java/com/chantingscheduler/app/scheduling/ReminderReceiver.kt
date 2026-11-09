package com.chantingscheduler.app.scheduling

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chantingscheduler.app.data.ScheduleRepository
import com.chantingscheduler.app.core.scheduling.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires a few minutes before a session starts ([AlarmScheduler] arms it next
 * to the main alarm) and posts a quiet heads-up-able notification so the
 * practitioner can prepare. Never starts playback itself.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ScheduleRepository

    @SuppressLint("MissingPermission") // areNotificationsEnabled() guards the post
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId < 0) return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 0)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val item = repository.getById(scheduleId)
                if (item != null && item.schedule.enabled &&
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                ) {
                    val openApp = PendingIntent.getActivity(
                        context,
                        scheduleId.toInt(),
                        Intent()
                            .setClassName(context.packageName, "com.chantingscheduler.app.MainActivity")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val name = item.schedule.label.ifBlank {
                        context.getString(com.chantingscheduler.app.core.scheduling.R.string.reminder_default_label)
                    }
                    val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                        .setSmallIcon(com.chantingscheduler.app.core.scheduling.R.drawable.ic_reminder)
                        .setContentTitle(context.getString(R.string.reminder_title))
                        .setContentText(
                            context.getString(R.string.reminder_text, name, minutes),
                        )
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setAutoCancel(true)
                        .setContentIntent(openApp)
                        .build()
                    runCatching {
                        NotificationManagerCompat.from(context)
                            .notify(NOTIFICATION_ID_BASE + scheduleId.toInt(), notification)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.chantingscheduler.app.action.REMIND"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        const val EXTRA_MINUTES = "minutes"
        const val CHANNEL_REMINDERS = "reminders"
        private const val NOTIFICATION_ID_BASE = 10_000
    }
}
