package com.chantingscheduler.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StreakCalculatorAudioTest {

    private val utc: ZoneOffset = ZoneOffset.UTC

    private fun day(ms: Long) = ms

    @Test
    fun `minutes prefer audio time and fall back to wall clock per row`() {
        val sessions = listOf(
            // audio time known: 90s -> 2 min (rounded)
            SessionPoint(day(0L), durationSeconds = 600, audioSeconds = 90),
            // legacy row without audio time: wall clock 61s -> 1 min
            SessionPoint(day(86_400_000L), durationSeconds = 61, audioSeconds = null),
        )
        assertEquals(3, StreakCalculator.totalMinutes(sessions))
    }

    @Test
    fun `minutes by day use audio time when present`() {
        val base = LocalDate.of(2026, 3, 9).atStartOfDay(utc).toInstant().toEpochMilli()
        val sessions = listOf(
            SessionPoint(base, durationSeconds = 3_600, audioSeconds = 300),
        )
        assertEquals(5, StreakCalculator.minutesByDay(sessions, utc)[LocalDate.of(2026, 3, 9)])
    }

    @Test
    fun `longest streak finds the best run anywhere in the past`() {
        val base = LocalDate.of(2026, 3, 9).atStartOfDay(utc).toInstant().toEpochMilli()
        fun at(d: Int) = SessionPoint(base + d * 86_400_000L, durationSeconds = 60)
        val sessions = listOf(
            at(0), at(1), at(2), // 3-day run
            at(6), at(7), at(8), at(9), at(10), // 5-day run (day 3–5 is a gap)
        )
        // "Today" is after every session, so both runs are in the past.
        assertEquals(5, StreakCalculator.longestStreak(sessions, utc, LocalDate.of(2026, 3, 24)))
    }

    @Test
    fun `longest streak matches current streak when the run reaches today`() {
        val base = LocalDate.of(2026, 3, 9).atStartOfDay(utc).toInstant().toEpochMilli()
        val sessions = listOf(
            SessionPoint(base - 86_400_000L, 60),
            SessionPoint(base, 60),
        )
        assertEquals(2, StreakCalculator.longestStreak(sessions, utc, LocalDate.of(2026, 3, 9)))
    }

    @Test
    fun `longest streak ignores days after today`() {
        val base = LocalDate.of(2026, 3, 9).atStartOfDay(utc).toInstant().toEpochMilli()
        val sessions = listOf(
            SessionPoint(base, 60),
            SessionPoint(base + 86_400_000L, 60), // tomorrow (clock drift/test leftover)
        )
        assertEquals(1, StreakCalculator.longestStreak(sessions, utc, LocalDate.of(2026, 3, 9)))
    }

    @Test
    fun `longest streak of empty history is zero`() {
        assertEquals(0, StreakCalculator.longestStreak(emptyList(), utc))
    }
}
