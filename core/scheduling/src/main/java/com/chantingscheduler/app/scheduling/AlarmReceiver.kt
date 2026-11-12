package com.chantingscheduler.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.chantingscheduler.app.data.AlarmLedgerStore
import com.chantingscheduler.app.data.PlaybackSettingsStore
import com.chantingscheduler.app.data.ScheduleRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fires when an alarm goes off: starts playback and schedules the next occurrence. */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var sessionStarter: SessionStarter

    @Inject
    lateinit var repository: ScheduleRepository

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        if (scheduleId < 0) return

        // First thing, before any async work: record that the alarm fired.
        // Even if the process is killed mid-handling, the ledger now knows.
        AlarmLedgerStore.get(context).markFired(scheduleId, System.currentTimeMillis())

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val item = repository.getById(scheduleId)
                if (item != null && item.schedule.enabled) {
                    // Whether this was the regular alarm or a snooze, any
                    // persisted snooze for this schedule has now fired.
                    PlaybackSettingsStore.get(context).clearPendingSnooze(scheduleId)
                    if (item.sounds.isNotEmpty() || item.steps.isNotEmpty()) {
                        sessionStarter.start(item)
                    }
                    // One-shot alarms: re-arm for the next matching day.
                    alarmScheduler.schedule(item.schedule)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.chantingscheduler.app.action.ALARM_FIRE"
        const val EXTRA_SCHEDULE_ID = "schedule_id"
    }
}
