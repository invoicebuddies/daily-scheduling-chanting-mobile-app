package com.chantingscheduler.app.scheduling

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chantingscheduler.app.data.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-arms all enabled schedules whenever the system invalidates or changes
 * the conditions alarms were scheduled under:
 *
 * - [Intent.ACTION_BOOT_COMPLETED] — alarms never survive a reboot.
 * - [Intent.ACTION_MY_PACKAGE_REPLACED] — alarms may be dropped when the app
 *   is updated; re-arm on the new version's first launch signal.
 * - [Intent.ACTION_TIME_CHANGED] / [Intent.ACTION_TIMEZONE_CHANGED] — users
 *   travel; fixed times and sunrise/sunset must follow the new wall clock/zone.
 * - [AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED] —
 *   re-arm as exact alarms once the user grants the permission.
 *
 * `LOCKED_BOOT_COMPLETED` is deliberately not handled: the Room database and
 * SharedPreferences live in credential-protected storage that isn't readable
 * before the user unlocks the device, so re-arming that early would fail.
 * `BOOT_COMPLETED` fires after the user unlocks anyway on first boot.
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ScheduleRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> rearm()
            else -> Unit
        }
    }

    private fun rearm() {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                alarmScheduler.rearmAll(repository.getEnabled().map { it.schedule })
            } finally {
                pendingResult.finish()
            }
        }
    }
}
