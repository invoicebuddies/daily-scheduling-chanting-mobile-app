package com.chantingscheduler.app.scheduling

import com.chantingscheduler.app.data.ALL_DAYS
import com.chantingscheduler.app.data.LatLng
import com.chantingscheduler.app.data.ScheduleEntity
import com.chantingscheduler.app.data.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TriggerCalculatorTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun fixed(hour: Int, minute: Int, days: Int = ALL_DAYS) =
        ScheduleEntity(hour = hour, minute = minute, daysOfWeek = days)

    private fun millis(zdt: ZonedDateTime): Long = zdt.toInstant().toEpochMilli()

    @Test
    fun `fixed time later today fires today`() {
        // 2026-03-09 is a Monday. 10:00 with from = 09:00 same day.
        val from = millis(ZonedDateTime.of(2026, 3, 9, 9, 0, 0, 0, utc))
        val result = TriggerCalculator.nextTriggerMillis(fixed(10, 0), null, utc, from)
        assertEquals(millis(ZonedDateTime.of(2026, 3, 9, 10, 0, 0, 0, utc)), result)
    }

    @Test
    fun `fixed time already passed today rolls to tomorrow`() {
        val from = millis(ZonedDateTime.of(2026, 3, 9, 12, 0, 0, 0, utc))
        val result = TriggerCalculator.nextTriggerMillis(fixed(10, 0), null, utc, from)
        assertEquals(millis(ZonedDateTime.of(2026, 3, 10, 10, 0, 0, 0, utc)), result)
    }

    @Test
    fun `days-of-week mask skips non-matching days`() {
        // Monday-only schedule, from Monday 12:00: today 10:00 has passed,
        // the rest of the week is skipped, next fire is next Monday.
        val mondayOnly = fixed(10, 0, days = 0b0000001)
        val from = millis(ZonedDateTime.of(2026, 3, 9, 12, 0, 0, 0, utc))
        val result = TriggerCalculator.nextTriggerMillis(mondayOnly, null, utc, from)
        assertEquals(millis(ZonedDateTime.of(2026, 3, 16, 10, 0, 0, 0, utc)), result)
    }

    @Test
    fun `sunrise trigger honors a negative offset`() {
        val bangkok = LatLng(latitude = 13.75, longitude = 100.5, address = null)
        val date = LocalDate.of(2025, 6, 21)
        val sunrise = SunTimesCalculator.sunrise(date, bangkok, utc)!!
        val schedule = ScheduleEntity(
            hour = 0, minute = 0, triggerType = TriggerType.SUNRISE, offsetMinutes = -30,
        )
        val from = millis(date.atStartOfDay(utc))
        val result = TriggerCalculator.nextTriggerMillis(schedule, bangkok, utc, from)
        assertEquals(millis(sunrise.minusMinutes(30)), result)
    }

    @Test
    fun `sunset trigger honors a positive offset`() {
        val bangkok = LatLng(latitude = 13.75, longitude = 100.5, address = null)
        val date = LocalDate.of(2025, 6, 21)
        val sunset = SunTimesCalculator.sunset(date, bangkok, utc)!!
        val schedule = ScheduleEntity(
            hour = 0, minute = 0, triggerType = TriggerType.SUNSET, offsetMinutes = 45,
        )
        val from = millis(date.atStartOfDay(utc))
        val result = TriggerCalculator.nextTriggerMillis(schedule, bangkok, utc, from)
        assertEquals(millis(sunset.plusMinutes(45)), result)
    }

    @Test
    fun `fixed alarm on a DST spring-forward gap resolves past the gap`() {
        // America/New_York springs forward 2026-03-08 02:00 -> 03:00.
        // A 02:30 alarm lands in the gap; java.time pushes it to 03:30 EDT.
        val newYork = ZoneId.of("America/New_York")
        val schedule = fixed(2, 30)
        val from = millis(ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, newYork))
        val result = TriggerCalculator.nextTriggerMillis(schedule, null, newYork, from)
        assertEquals(
            millis(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, newYork)),
            result,
        )
    }

    @Test
    fun `polar-night location with no sunrise in the window returns -1`() {
        // Longyearbyen (Svalbard) in late December: no sunrise for weeks, so
        // no candidate exists within the 8-day search window.
        val svalbard = LatLng(latitude = 78.22, longitude = 15.65, address = null)
        val schedule = ScheduleEntity(hour = 0, minute = 0, triggerType = TriggerType.SUNRISE)
        val from = millis(ZonedDateTime.of(2026, 12, 20, 0, 0, 0, 0, utc))
        val result = TriggerCalculator.nextTriggerMillis(schedule, svalbard, utc, from)
        assertEquals(-1L, result)
    }

    @Test
    fun `sun trigger without a location returns -1`() {
        val schedule = ScheduleEntity(hour = 6, minute = 0, triggerType = TriggerType.SUNSET)
        val from = millis(ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, utc))
        assertEquals(-1L, TriggerCalculator.nextTriggerMillis(schedule, null, utc, from))
    }

    @Test
    fun `no days selected returns -1`() {
        val from = millis(ZonedDateTime.of(2026, 3, 9, 0, 0, 0, 0, utc))
        assertEquals(-1L, TriggerCalculator.nextTriggerMillis(fixed(10, 0, days = 0), null, utc, from))
    }

    @Test
    fun `full moon fires at the schedule time on the full-moon date`() {
        // Full moon on 2026-01-03 (UTC).
        val schedule = ScheduleEntity(
            hour = 7, minute = 0, triggerType = TriggerType.FULL_MOON, daysOfWeek = 0,
        )
        val from = millis(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, utc))
        val result = TriggerCalculator.nextTriggerMillis(schedule, null, utc, from)
        assertEquals(millis(ZonedDateTime.of(2026, 1, 3, 7, 0, 0, 0, utc)), result)
    }

    @Test
    fun `full moon whose time already passed rolls to the next lunar month`() {
        val schedule = ScheduleEntity(
            hour = 7, minute = 0, triggerType = TriggerType.FULL_MOON, daysOfWeek = 0,
        )
        // 2026-01-03 08:00 is after the 07:00 slot: next full moon is 2026-02-01.
        val from = millis(ZonedDateTime.of(2026, 1, 3, 8, 0, 0, 0, utc))
        val result = TriggerCalculator.nextTriggerMillis(schedule, null, utc, from)
        assertEquals(millis(ZonedDateTime.of(2026, 2, 1, 7, 0, 0, 0, utc)), result)
    }

    @Test
    fun `new moon fires at the schedule time on the new-moon date`() {
        // New moon on 2026-01-18 (UTC).
        val schedule = ScheduleEntity(
            hour = 7, minute = 0, triggerType = TriggerType.NEW_MOON, daysOfWeek = 0,
        )
        val from = millis(ZonedDateTime.of(2026, 1, 10, 0, 0, 0, 0, utc))
        val result = TriggerCalculator.nextTriggerMillis(schedule, null, utc, from)
        assertEquals(millis(ZonedDateTime.of(2026, 1, 18, 7, 0, 0, 0, utc)), result)
    }

    @Test
    fun `lunar triggers ignore the days-of-week mask`() {
        val schedule = ScheduleEntity(
            hour = 7, minute = 0, triggerType = TriggerType.FULL_MOON,
            daysOfWeek = 0b0000001, // Monday only — 2026-01-03 is a Saturday.
        )
        val from = millis(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, utc))
        val result = TriggerCalculator.nextTriggerMillis(schedule, null, utc, from)
        assertEquals(millis(ZonedDateTime.of(2026, 1, 3, 7, 0, 0, 0, utc)), result)
    }

    @Test
    fun `trigger strictly at from is not eligible until the next matching day`() {
        // Candidate millis must be strictly after `from`; an exactly-at-from
        // candidate rolls forward like any other passed time.
        val from = millis(ZonedDateTime.of(2026, 3, 9, 10, 0, 0, 0, utc))
        val result = TriggerCalculator.nextTriggerMillis(fixed(10, 0), null, utc, from)
        assertEquals(millis(ZonedDateTime.of(2026, 3, 10, 10, 0, 0, 0, utc)), result)
    }

    private fun LocalDate.atStartOfDay(zone: ZoneId): ZonedDateTime = atTime(0, 0).atZone(zone)
}
