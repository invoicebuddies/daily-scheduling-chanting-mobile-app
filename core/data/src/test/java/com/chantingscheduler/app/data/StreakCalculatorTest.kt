package com.chantingscheduler.app.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 3, 14) // a Saturday

    private fun sessionOn(date: LocalDate, hour: Int = 6, durationSeconds: Int = 600) =
        SessionPoint(
            startedAtEpochMs = ZonedDateTime.of(date.atTime(hour, 0), zone).toInstant().toEpochMilli(),
            durationSeconds = durationSeconds,
        )

    @Test
    fun `empty history has no streak`() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), zone, today))
    }

    @Test
    fun `single session today gives streak of one`() {
        assertEquals(1, StreakCalculator.currentStreak(listOf(sessionOn(today)), zone, today))
    }

    @Test
    fun `session yesterday keeps the streak alive while today is pending`() {
        assertEquals(1, StreakCalculator.currentStreak(listOf(sessionOn(today.minusDays(1))), zone, today))
    }

    @Test
    fun `session two days ago means no streak`() {
        assertEquals(0, StreakCalculator.currentStreak(listOf(sessionOn(today.minusDays(2))), zone, today))
    }

    @Test
    fun `consecutive days accumulate`() {
        val sessions = listOf(
            sessionOn(today),
            sessionOn(today.minusDays(1)),
            sessionOn(today.minusDays(2)),
        )
        assertEquals(3, StreakCalculator.currentStreak(sessions, zone, today))
    }

    @Test
    fun `multiple sessions on the same day count once`() {
        val sessions = listOf(
            sessionOn(today, hour = 6),
            sessionOn(today, hour = 18),
            sessionOn(today.minusDays(1)),
        )
        assertEquals(2, StreakCalculator.currentStreak(sessions, zone, today))
    }

    @Test
    fun `a gap day breaks the streak`() {
        val sessions = listOf(
            sessionOn(today),
            sessionOn(today.minusDays(1)),
            sessionOn(today.minusDays(3)),
            sessionOn(today.minusDays(4)),
        )
        assertEquals(2, StreakCalculator.currentStreak(sessions, zone, today))
    }

    @Test
    fun `day boundaries follow the zone not UTC wall time`() {
        // 23:30 local on day X must count for day X, not the UTC next day.
        val nonUtc = ZoneId.of("Pacific/Auckland") // UTC+13 in March
        val session = SessionPoint(
            startedAtEpochMs = ZonedDateTime.of(today.atTime(23, 30), nonUtc).toInstant().toEpochMilli(),
            durationSeconds = 600,
        )
        assertEquals(1, StreakCalculator.currentStreak(listOf(session), nonUtc, today))
    }

    @Test
    fun `total minutes rounds to nearest`() {
        assertEquals(1, StreakCalculator.totalMinutes(listOf(sessionOn(today, durationSeconds = 45))))
        assertEquals(0, StreakCalculator.totalMinutes(listOf(sessionOn(today, durationSeconds = 29))))
        assertEquals(2, StreakCalculator.totalMinutes(listOf(sessionOn(today, durationSeconds = 100))))
    }

    @Test
    fun `this week starts Monday`() {
        val thisMonday = LocalDate.of(2026, 3, 9)
        val lastSunday = LocalDate.of(2026, 3, 8)
        val sessions = listOf(
            sessionOn(thisMonday, durationSeconds = 600),
            sessionOn(today, durationSeconds = 600),
            sessionOn(lastSunday, durationSeconds = 3600), // previous week: excluded
        )
        assertEquals(20, StreakCalculator.minutesThisWeek(sessions, zone, today))
    }

    @Test
    fun `minutes by day groups same-day sessions`() {
        val sessions = listOf(
            sessionOn(today, hour = 6, durationSeconds = 600),
            sessionOn(today, hour = 18, durationSeconds = 600),
            sessionOn(today.minusDays(1), durationSeconds = 1800),
        )
        val byDay = StreakCalculator.minutesByDay(sessions, zone)
        assertEquals(2, byDay.size)
        assertEquals(20, byDay[today])
        assertEquals(30, byDay[today.minusDays(1)])
    }
}
