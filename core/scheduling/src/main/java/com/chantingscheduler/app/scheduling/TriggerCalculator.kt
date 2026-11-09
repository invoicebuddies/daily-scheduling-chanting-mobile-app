package com.chantingscheduler.app.scheduling

import com.chantingscheduler.app.data.LatLng
import com.chantingscheduler.app.data.ScheduleEntity
import com.chantingscheduler.app.data.TriggerType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.shredzone.commons.suncalc.MoonPhase

/**
 * Pure computation of the next moment a schedule should fire. Kept free of
 * `Context` (no [com.chantingscheduler.app.data.LocationStore] lookups) so the
 * whole trigger logic is unit-testable; callers resolve the location and zone.
 *
 * Returns the next epoch-millis strictly after [from], or -1 when nothing can
 * be scheduled (no day selected, or a sun trigger without a location — such
 * schedules are silently skipped and the UI shows a warning banner).
 *
 * Lunar triggers (FULL_MOON / NEW_MOON) fire at the schedule's hour:minute on
 * the local date of the next full/new moon; they ignore [ScheduleEntity.daysOfWeek].
 */
object TriggerCalculator {

    fun nextTriggerMillis(
        schedule: ScheduleEntity,
        location: LatLng?,
        zone: ZoneId,
        from: Long,
    ): Long {
        val lunar = schedule.triggerType.isLunar
        if (schedule.daysOfWeek == 0 && !lunar) return -1L
        val needsLocation = when (schedule.triggerType) {
            TriggerType.SUNRISE, TriggerType.SUNSET -> true
            else -> false
        }
        if (needsLocation && location == null) return -1L
        val fromDate = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        if (lunar) {
            // The phase date is somewhere in the current ~29.5-day cycle;
            // fire at the schedule's time on the first such date after `from`.
            var date = fromDate
            for (i in 0..31) {
                if (moonOccursOn(date, zone, schedule.triggerType.moonPhase)) {
                    val millis = date.atTime(schedule.hour, schedule.minute)
                        .atZone(zone).toInstant().toEpochMilli()
                    if (millis > from) return millis
                }
                date = date.plusDays(1)
            }
            return -1L
        }
        for (dayOffset in 0..7) {
            val date = fromDate.plusDays(dayOffset.toLong())
            val candidate = triggerInstant(schedule, date, location, zone) ?: continue
            val millis = candidate.toInstant().toEpochMilli()
            if (millis <= from) continue
            // java.time DayOfWeek: MONDAY=1..SUNDAY=7 -> bitmask: Monday=0..Sunday=6
            val bit = candidate.dayOfWeek.value - 1
            if (schedule.daysOfWeek and (1 shl bit) != 0) return millis
        }
        return -1L
    }

    internal fun triggerInstant(
        schedule: ScheduleEntity,
        date: LocalDate,
        location: LatLng?,
        zone: ZoneId,
    ): ZonedDateTime? = when (schedule.triggerType) {
        TriggerType.FIXED -> date.atTime(schedule.hour, schedule.minute).atZone(zone)
        // commons-suncalc returns the *next* rise/set even when it falls
        // outside the queried day (e.g. the end of polar night months later);
        // only instants actually on [date] are candidates for that day.
        TriggerType.SUNRISE -> SunTimesCalculator.sunrise(date, location!!, zone)
            ?.takeIf { it.toLocalDate() == date }
            ?.plusMinutes(schedule.offsetMinutes.toLong())
        TriggerType.SUNSET -> SunTimesCalculator.sunset(date, location!!, zone)
            ?.takeIf { it.toLocalDate() == date }
            ?.plusMinutes(schedule.offsetMinutes.toLong())
        TriggerType.FULL_MOON, TriggerType.NEW_MOON -> null
    }

    /** True when the given lunar phase occurs somewhere on [date] in [zone]. */
    internal fun moonOccursOn(date: LocalDate, zone: ZoneId, phase: MoonPhase.Phase): Boolean {
        val time = MoonPhase.compute()
            .on(date.year, date.monthValue, date.dayOfMonth)
            .timezone(TimeZone.getTimeZone(zone))
            .phase(phase)
            .execute()
            .time
        return time.toLocalDate() == date
    }
}

private val TriggerType.isLunar: Boolean get() = this == TriggerType.FULL_MOON || this == TriggerType.NEW_MOON

private val TriggerType.moonPhase: MoonPhase.Phase
    get() = when (this) {
        TriggerType.FULL_MOON -> MoonPhase.Phase.FULL_MOON
        TriggerType.NEW_MOON -> MoonPhase.Phase.NEW_MOON
        else -> error("Not a lunar trigger: $this")
    }
