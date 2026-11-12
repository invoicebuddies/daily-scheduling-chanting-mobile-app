package com.chantingscheduler.app.scheduling

import com.chantingscheduler.app.data.AlarmLedgerStore

/**
 * Pure decision logic behind the "your chant didn't play" card.
 *
 * A schedule counts as missed when ALL of these hold:
 * - it is still enabled (edit/disable/delete re-arms or clears the ledger
 *   entry, so an entry can only outlive its schedule via a kill);
 * - its expected fire time is between [MIN_AGE_MS] and [MAX_AGE_MS] in the
 *   past (younger = the receiver may still be finishing; older = stale);
 * - it never fired at or after the expected time (minus a small tolerance
 *   for exact-alarm skew: a fire a few seconds early is a fire).
 *
 * Every returned miss is moved out of the ledger by the caller, so each miss
 * is reported exactly once; [Result.clearScheduleIds] also covers schedules
 * that were deleted while their entry lingered.
 */
object MissedChantDetector {

    /** 10 minutes: an exact alarm may legally be a touch late while its receiver runs. */
    const val MIN_AGE_MS = 10 * 60_000L

    /** 7 days: older misses are stale news — clear them without a card. */
    const val MAX_AGE_MS = 7 * 24 * 60 * 60_000L

    /** A fire this early still counts as on time (clock skew / early delivery). */
    const val EARLY_FIRE_TOLERANCE_MS = 60_000L

    data class Missed(val scheduleId: Long, val expectedAtMs: Long)

    data class Result(
        val missed: List<Missed>,
        val clearScheduleIds: Set<Long>,
    )

    /**
     * @param ledger what was armed vs what fired (see [AlarmLedgerStore])
     * @param enabledScheduleIds schedules currently enabled; anything else is
     *   treated as deleted and its entry is cleared, never reported
     */
    fun detect(
        ledger: Map<Long, AlarmLedgerStore.LedgerEntry>,
        enabledScheduleIds: Set<Long>,
        nowMs: Long,
        minAgeMs: Long = MIN_AGE_MS,
        maxAgeMs: Long = MAX_AGE_MS,
        earlyFireToleranceMs: Long = EARLY_FIRE_TOLERANCE_MS,
    ): Result {
        val missed = mutableListOf<Missed>()
        val clear = mutableSetOf<Long>()
        ledger.forEach { (scheduleId, entry) ->
            val expectedAt = entry.expectedAtMs
            when {
                // Schedule deleted (or disabled — cancel clears the entry, so
                // reaching here means the promise is moot either way).
                scheduleId !in enabledScheduleIds -> clear.add(scheduleId)
                // Too recent: the receiver may still be mid-flight.
                expectedAt > nowMs - minAgeMs -> Unit
                // Stale: beyond the reporting window.
                expectedAt < nowMs - maxAgeMs -> clear.add(scheduleId)
                // Fired on time (allowing a touch of early skew): not missed.
                // The entry stays — the fired receiver re-arms over it.
                entry.firedAtMs?.let { it >= expectedAt - earlyFireToleranceMs } == true -> Unit
                else -> {
                    missed.add(Missed(scheduleId, expectedAt))
                    // The caller moves reported misses out of the ledger so
                    // each one is reported exactly once.
                    clear.add(scheduleId)
                }
            }
        }
        return Result(missed, clear)
    }
}
