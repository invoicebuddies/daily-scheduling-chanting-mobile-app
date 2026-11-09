package com.chantingscheduler.app.scheduling

/**
 * Pure decision logic for restoring snoozes after a reboot, time change, or
 * force-stop: alarm PendingIntents never survive those events, so pending
 * snoozes are persisted and re-armed here. Only future snoozes are restored;
 * past ones are dropped (their moment has passed) and must also be removed
 * from the store by the caller.
 */
object SnoozeRestore {

    /** Of the stored [entries] (scheduleId → triggerAt), the ones still in the future at [now]. */
    fun pendingToRestore(entries: Map<Long, Long>, now: Long): Map<Long, Long> =
        entries.filterValues { it > now }
}
