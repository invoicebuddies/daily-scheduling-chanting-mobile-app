package com.chantingscheduler.app.scheduling

import com.chantingscheduler.app.data.AlarmLedgerStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedChantDetectorTest {

    private val now = 10_000_000L
    private val recent = now - 60 * 60_000L // one hour ago
    private val onTimeFire = recent + 5_000L

    private fun entry(expectedAt: Long, firedAt: Long? = null) =
        AlarmLedgerStore.LedgerEntry(expectedAtMs = expectedAt, firedAtMs = firedAt)

    @Test
    fun `normal fire is not missed`() {
        val result = MissedChantDetector.detect(
            ledger = mapOf(1L to entry(recent, onTimeFire)),
            enabledScheduleIds = setOf(1L),
            nowMs = now,
        )
        assertTrue(result.missed.isEmpty())
    }

    @Test
    fun `fire slightly before expected time is not missed`() {
        // Exact alarms can deliver a few seconds early — skew, not a miss.
        val result = MissedChantDetector.detect(
            ledger = mapOf(1L to entry(recent, recent - 30_000L)),
            enabledScheduleIds = setOf(1L),
            nowMs = now,
        )
        assertTrue(result.missed.isEmpty())
    }

    @Test
    fun `missed alarm is detected`() {
        val result = MissedChantDetector.detect(
            ledger = mapOf(1L to entry(recent)),
            enabledScheduleIds = setOf(1L),
            nowMs = now,
        )
        assertEquals(listOf(MissedChantDetector.Missed(1L, recent)), result.missed)
        // The caller clears the ledger entry so the miss is reported once.
        assertEquals(setOf(1L), result.clearScheduleIds)
    }

    @Test
    fun `missed alarm is detected exactly once`() {
        val ledger = mutableMapOf(1L to entry(recent))
        val first = MissedChantDetector.detect(ledger, setOf(1L), now)
        assertEquals(1, first.missed.size)
        // …once cleared, a later pass sees nothing.
        first.missed.forEach { ledger.remove(it.scheduleId) }
        val second = MissedChantDetector.detect(ledger, setOf(1L), now)
        assertTrue(second.missed.isEmpty())
    }

    @Test
    fun `stale fired timestamp older than expected counts as missed`() {
        // Yesterday's fire followed by a re-arm that never happened: the
        // fired timestamp predates the current expectation by far more
        // than the skew tolerance.
        val result = MissedChantDetector.detect(
            ledger = mapOf(1L to entry(recent, recent - 24 * 60 * 60_000L)),
            enabledScheduleIds = setOf(1L),
            nowMs = now,
        )
        assertEquals(listOf(MissedChantDetector.Missed(1L, recent)), result.missed)
    }

    @Test
    fun `schedule disabled after arming is not missed`() {
        val result = MissedChantDetector.detect(
            ledger = mapOf(1L to entry(recent)),
            enabledScheduleIds = emptySet(),
            nowMs = now,
        )
        assertTrue(result.missed.isEmpty())
        assertEquals(setOf(1L), result.clearScheduleIds)
    }

    @Test
    fun `schedule deleted after arming is not missed`() {
        val result = MissedChantDetector.detect(
            ledger = mapOf(1L to entry(recent), 2L to entry(recent)),
            enabledScheduleIds = setOf(2L),
            nowMs = now,
        )
        assertEquals(listOf(MissedChantDetector.Missed(2L, recent)), result.missed)
        // Deleted schedule cleared, reported miss cleared after its one card.
        assertEquals(setOf(1L, 2L), result.clearScheduleIds)
    }

    @Test
    fun `snooze does not cause a false positive`() {
        // A fired alarm re-arms the next occurrence into the future; a missed
        // *snooze* never touches the main ledger. Either way: no card.
        val futureExpectation = now + 60 * 60_000L
        val result = MissedChantDetector.detect(
            ledger = mapOf(1L to entry(futureExpectation, now - 10 * 60_000L)),
            enabledScheduleIds = setOf(1L),
            nowMs = now,
        )
        assertTrue(result.missed.isEmpty())
    }

    @Test
    fun `alarm five minutes late is still within the grace window`() {
        val justMissed = now - 5 * 60_000L
        val result = MissedChantDetector.detect(
            ledger = mapOf(1L to entry(justMissed)),
            enabledScheduleIds = setOf(1L),
            nowMs = now,
        )
        assertTrue(result.missed.isEmpty())
    }

    @Test
    fun `alarm older than a week is cleared without a card`() {
        val ancient = now - 8 * 24 * 60 * 60_000L
        val result = MissedChantDetector.detect(
            ledger = mapOf(1L to entry(ancient)),
            enabledScheduleIds = setOf(1L),
            nowMs = now,
        )
        assertTrue(result.missed.isEmpty())
        assertEquals(setOf(1L), result.clearScheduleIds)
    }
}
