package com.chantingscheduler.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Honest record of what the alarm system promised vs what it delivered.
 *
 * - [markExpected] is written whenever [com.chantingscheduler.app.scheduling.AlarmScheduler]
 *   arms a schedule's main alarm; any later edit/disable/delete cancels and
 *   re-arms (or clears), so a stale expectation can't outlive its schedule.
 * - [markFired] is written by the alarm receiver as its very first act, before
 *   any database work, so even a killed receiver leaves the fire recorded.
 * - When an expected alarm never fired, [com.chantingscheduler.app.scheduling.MissedChantDetector]
 *   moves it to the missed list so the list screen can tell the user once.
 *
 * SharedPreferences (same pattern as [PlaybackSettingsStore]) so receivers
 * and the scheduler can write synchronously; no Room schema change needed.
 */
class AlarmLedgerStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Records that [scheduleId]'s main alarm was armed for [expectedAtMs]. */
    fun markExpected(scheduleId: Long, expectedAtMs: Long) {
        prefs.edit().putLong(expectedKey(scheduleId), expectedAtMs).apply()
    }

    /** Records that [scheduleId]'s alarm actually fired at [firedAtMs]. */
    fun markFired(scheduleId: Long, firedAtMs: Long) {
        prefs.edit().putLong(firedKey(scheduleId), firedAtMs).apply()
    }

    /** One entry per schedule that currently has an armed main alarm. */
    fun entries(): Map<Long, LedgerEntry> {
        val result = mutableMapOf<Long, LedgerEntry>()
        prefs.all.forEach { (key, value) ->
            if (value !is Long) return@forEach
            val id = key.removePrefix(EXPECTED_KEY_PREFIX).toLongOrNull()
            when {
                id != null -> result[id] = result[id]?.copy(expectedAtMs = value)
                    ?: LedgerEntry(expectedAtMs = value, firedAtMs = null)
                else -> key.removePrefix(FIRED_KEY_PREFIX).toLongOrNull()?.let { firedId ->
                    result[firedId] = result[firedId]?.copy(firedAtMs = value)
                        ?: LedgerEntry(expectedAtMs = 0, firedAtMs = value)
                }
            }
        }
        // Entries with no expectation are leftovers (fired after cancel);
        // they carry no promise and are ignored by the detector.
        return result.filterValues { it.expectedAtMs > 0 }
    }

    /** Drops both keys for [scheduleId] (fired-and-reported, disabled, deleted). */
    fun clear(scheduleId: Long) {
        prefs.edit()
            .remove(expectedKey(scheduleId))
            .remove(firedKey(scheduleId))
            .apply()
    }

    // Reported misses (pending card), stored as "id:expectedAt" strings.
    private val _missed = MutableStateFlow(readMissed())

    /** Pending misses, always current — collectors see them the moment
     * detection lands, without waiting for the next app open. */
    val missed: StateFlow<Set<MissedChant>> = _missed.asStateFlow()

    /** Adds misses to the pending list (idempotent per schedule+expectedAt). */
    fun addMissed(misses: List<MissedChant>) {
        if (misses.isEmpty()) return
        val current = _missed.value.toMutableSet()
        current.addAll(misses)
        _missed.value = current
        persistMissed(current)
    }

    /** The misses awaiting their one-time card; survives until dismissed. */
    fun missedChants(): Set<MissedChant> = _missed.value

    /** The user saw the card (or the schedules are gone): nothing pending. */
    fun clearMissed() {
        _missed.value = emptySet()
        prefs.edit().remove(KEY_MISSED).apply()
    }

    private fun readMissed(): Set<MissedChant> =
        prefs.getStringSet(KEY_MISSED, emptySet()).orEmpty().mapNotNull { encoded ->
            val parts = encoded.split(MISSED_SEPARATOR)
            val id = parts.getOrNull(0)?.toLongOrNull()
            val at = parts.getOrNull(1)?.toLongOrNull()
            if (id != null && at != null) MissedChant(id, at) else null
        }.toSet()

    private fun persistMissed(misses: Set<MissedChant>) {
        prefs.edit().putStringSet(
            KEY_MISSED,
            misses.map { "${it.scheduleId}$MISSED_SEPARATOR${it.expectedAtMs}" }.toSet(),
        ).apply()
    }

    // endregion

    data class LedgerEntry(
        /** Epoch millis at which the schedule's alarm was armed to fire. */
        val expectedAtMs: Long,
        /** Epoch millis at which the alarm actually fired, if it did. */
        val firedAtMs: Long? = null,
    )

    /** A chant that was expected but never played, reported exactly once. */
    data class MissedChant(val scheduleId: Long, val expectedAtMs: Long)

    private fun expectedKey(scheduleId: Long) = "$EXPECTED_KEY_PREFIX$scheduleId"
    private fun firedKey(scheduleId: Long) = "$FIRED_KEY_PREFIX$scheduleId"

    companion object {
        const val PREFS_NAME = "alarm_ledger"
        const val EXPECTED_KEY_PREFIX = "expected_"
        const val FIRED_KEY_PREFIX = "fired_"
        const val KEY_MISSED = "missed_chants"
        const val MISSED_SEPARATOR = ":"

        @Volatile
        private var instance: AlarmLedgerStore? = null

        fun get(context: Context): AlarmLedgerStore =
            instance ?: synchronized(this) {
                instance ?: AlarmLedgerStore(context).also { instance = it }
            }
    }
}
