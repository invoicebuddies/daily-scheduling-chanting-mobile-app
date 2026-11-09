package com.chantingscheduler.app.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Minimal view of a logged session for stats computation. [audioSeconds] is
 * the actual audio time (gaps/silence excluded) logged since schema v5; null
 * for older rows, which fall back to wall-clock [durationSeconds]. */
data class SessionPoint(
    val startedAtEpochMs: Long,
    val durationSeconds: Int,
    val audioSeconds: Int? = null,
)

/**
 * Pure stats math over logged sessions: streaks and mindful minutes.
 * All day bucketing uses the supplied zone (device default at call sites).
 */
object StreakCalculator {

    /**
     * Consecutive days with at least one session, counting back from [today].
     * A day not yet ended doesn't break the streak: if [today] has no session
     * yet, counting starts at yesterday.
     */
    fun currentStreak(
        sessions: List<SessionPoint>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Int {
        if (sessions.isEmpty()) return 0
        val days = sessions.mapTo(HashSet()) { dayOf(it.startedAtEpochMs, zone) }
        var cursor = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Total mindful minutes, seconds summed then rounded to nearest minute. */
    fun totalMinutes(sessions: List<SessionPoint>): Int =
        minutesFromSeconds(sessions.sumOf { effectiveSeconds(it) })

    /**
     * Longest run of consecutive days (anywhere in the past) with at least
     * one session. Days after [today] are ignored, matching [currentStreak]'s
     * "today doesn't break a streak" rule.
     */
    fun longestStreak(
        sessions: List<SessionPoint>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Int {
        if (sessions.isEmpty()) return 0
        val days = sessions.mapTo(HashSet()) { dayOf(it.startedAtEpochMs, zone) }
        var best = 0
        var run = 0
        var cursor = days.filter { !it.isAfter(today) }.minOrNull() ?: return 0
        val last = days.filter { !it.isAfter(today) }.maxOrNull()!!
        while (!cursor.isAfter(last)) {
            if (cursor in days) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
            cursor = cursor.plusDays(1)
        }
        return best
    }

    /** Mindful minutes in the current calendar week (Monday start). */
    fun minutesThisWeek(
        sessions: List<SessionPoint>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Int {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return totalMinutes(
            sessions.filter {
                dayOf(it.startedAtEpochMs, zone).let { day -> !day.isBefore(weekStart) && !day.isAfter(today) }
            },
        )
    }

    /** Mindful minutes per day (rounded per day), for calendar rendering. */
    fun minutesByDay(
        sessions: List<SessionPoint>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<LocalDate, Int> =
        sessions.groupBy { dayOf(it.startedAtEpochMs, zone) }
            .mapValues { (_, daySessions) ->
                minutesFromSeconds(daySessions.sumOf { effectiveSeconds(it) })
            }

    private fun effectiveSeconds(point: SessionPoint): Int =
        point.audioSeconds ?: point.durationSeconds

    private fun dayOf(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    private fun minutesFromSeconds(seconds: Int): Int = (seconds + 30) / 60
}
