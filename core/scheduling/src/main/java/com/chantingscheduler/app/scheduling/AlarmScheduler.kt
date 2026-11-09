package com.chantingscheduler.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.chantingscheduler.app.data.LocationStore
import com.chantingscheduler.app.data.PlaybackSettingsStore
import com.chantingscheduler.app.data.ScheduleEntity
import com.chantingscheduler.app.data.TriggerType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject

/**
 * Schedules one-shot alarms per schedule; recurrence is handled by
 * rescheduling the next occurrence each time an alarm fires ([AlarmReceiver]),
 * on system events ([RescheduleReceiver]), and on app start ([rearmAll] from
 * the application class).
 *
 * When the exact-alarm permission is held (or not required), alarms are armed
 * with [AlarmManager.setAlarmClock]: the most Doze- and OEM-resistant API, it
 * shows the alarm icon in the status bar and — unlike plain exact alarms —
 * permits starting a foreground service from the background. Without the
 * permission we fall back to an inexact wake-up alarm.
 */
class AlarmScheduler @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Next epoch-millis at which [schedule] should fire, or -1 if it cannot
     * be scheduled right now (see [TriggerCalculator]).
     */
    fun nextTriggerMillis(schedule: ScheduleEntity, from: Long = System.currentTimeMillis()): Long {
        val location = when (schedule.triggerType) {
            TriggerType.SUNRISE, TriggerType.SUNSET -> LocationStore.get(context).current
            else -> null
        }
        return TriggerCalculator.nextTriggerMillis(schedule, location, ZoneId.systemDefault(), from)
    }

    fun schedule(schedule: ScheduleEntity) {
        val triggerAt = nextTriggerMillis(schedule)
        if (triggerAt < 0) {
            // Not schedulable right now (e.g. sun trigger without a location):
            // make sure no stale alarm is left armed.
            cancel(schedule.id)
            return
        }
        arm(triggerAt, pendingIntent(schedule.id))
        // Pre-session reminder rides along on its own request-code range.
        // It must NOT use setAlarmClock: the lock screen and other apps would
        // show the reminder time as the device's "next alarm".
        val reminderMinutes = schedule.reminderMinutes
        if (reminderMinutes != null && reminderMinutes > 0) {
            val reminderAt = triggerAt - reminderMinutes * 60_000L
            if (reminderAt > System.currentTimeMillis()) {
                armReminder(reminderAt, reminderPendingIntent(schedule.id, reminderMinutes))
            } else {
                alarmManager.cancel(reminderPendingIntent(schedule.id, 0))
            }
        } else {
            alarmManager.cancel(reminderPendingIntent(schedule.id, 0))
        }
    }

    fun cancel(scheduleId: Long) {
        alarmManager.cancel(pendingIntent(scheduleId))
        alarmManager.cancel(reminderPendingIntent(scheduleId, 0))
    }

    /**
     * Arms a one-shot alarm for [scheduleId] at [triggerAtMillis],
     * independent of the schedule's regular alarm (separate PendingIntent
     * request code). When it fires, [AlarmReceiver] plays the schedule's
     * playlist without touching the regular re-arming. The snooze is also
     * persisted so it survives reboot / time change / force-stop (see
     * [rearmAll]).
     */
    fun scheduleSnooze(scheduleId: Long, triggerAtMillis: Long) {
        arm(triggerAtMillis, snoozePendingIntent(scheduleId))
        PlaybackSettingsStore.get(context).setPendingSnooze(scheduleId, triggerAtMillis)
    }

    fun cancelSnooze(scheduleId: Long) {
        alarmManager.cancel(snoozePendingIntent(scheduleId))
        PlaybackSettingsStore.get(context).clearPendingSnooze(scheduleId)
    }

    /**
     * Re-arms every enabled schedule; idempotent, safe to call on any trigger.
     * Also restores persisted snoozes that are still in the future and drops
     * the stale ones — alarm PendingIntents never survive a reboot, but the
     * user's snooze intent should.
     */
    fun rearmAll(enabledSchedules: List<ScheduleEntity>) {
        enabledSchedules.forEach { schedule(it) }
        val store = PlaybackSettingsStore.get(context)
        val pending = store.pendingSnoozes()
        val toRestore = SnoozeRestore.pendingToRestore(pending, System.currentTimeMillis())
        pending.keys.minus(toRestore.keys).forEach(store::clearPendingSnooze)
        toRestore.forEach { (scheduleId, triggerAt) ->
            arm(triggerAt, snoozePendingIntent(scheduleId))
        }
    }

    private fun arm(triggerAt: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAt, showIntent()),
                pendingIntent,
            )
        } else {
            // Exact-alarm permission not granted: fall back to inexact wake-up alarm.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /**
     * Reminders fire a notification only — no alarm-chip semantics. Exact when
     * permitted, inexact wake-up otherwise.
     */
    private fun armReminder(triggerAt: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** Tapping the status-bar alarm chip opens the app's home screen. */
    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        // MainActivity lives in the app module (downstream of this library),
        // so it is referenced by class name.
        Intent()
            .setClassName(context.packageName, "com.chantingscheduler.app.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun pendingIntent(scheduleId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        scheduleId.toInt(),
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun snoozePendingIntent(scheduleId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        scheduleId.toInt() + SNOOZE_REQUEST_CODE_OFFSET,
        Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_SCHEDULE_ID, scheduleId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Reminder alarms share one request-code range (independent of the
     * reminder length, so they stay cancellable); the minutes ride in the
     * extras and are refreshed by FLAG_UPDATE_CURRENT.
     */
    private fun reminderPendingIntent(scheduleId: Long, minutes: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            scheduleId.toInt() + REMINDER_REQUEST_CODE_OFFSET,
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_REMIND)
                .putExtra(ReminderReceiver.EXTRA_SCHEDULE_ID, scheduleId)
                .putExtra(ReminderReceiver.EXTRA_MINUTES, minutes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        // Keeps snooze PendingIntents distinct from regular schedule alarms,
        // whose request code is the schedule id itself.
        const val SNOOZE_REQUEST_CODE_OFFSET = 1_000_000_000
        const val REMINDER_REQUEST_CODE_OFFSET = 2_000_000_000
    }
}
