package com.chantingscheduler.app.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

class SunTimesCalculatorTest {

    @Test
    fun `equinox sunrise at equator and prime meridian is around 06 UTC`() {
        val sunrise = SunTimesCalculator.sunrise(
            LocalDate.of(2024, 3, 20), 0.0, 0.0, ZoneOffset.UTC,
        )
        assertNotNull(sunrise)
        val minutes = sunrise!!.hour * 60 + sunrise.minute
        assertTrue("sunrise at $sunrise", abs(minutes - 6 * 60) <= 10)
    }

    @Test
    fun `equinox sunset at equator and prime meridian is around 18 UTC`() {
        val sunset = SunTimesCalculator.sunset(
            LocalDate.of(2024, 3, 20), 0.0, 0.0, ZoneOffset.UTC,
        )
        assertNotNull(sunset)
        val minutes = sunset!!.hour * 60 + sunset.minute
        assertTrue("sunset at $sunset", abs(minutes - 18 * 60) <= 10)
    }

    @Test
    fun `sunrise is before sunset on the same day`() {
        val date = LocalDate.of(2025, 6, 21)
        // Bangkok civil time (ICT): both events belong to the queried day.
        val ict = ZoneOffset.ofHours(7)
        val sunrise = SunTimesCalculator.sunrise(date, 13.75, 100.5, ict) // Bangkok
        val sunset = SunTimesCalculator.sunset(date, 13.75, 100.5, ict)
        assertNotNull(sunrise)
        assertNotNull(sunset)
        assertTrue(sunrise!!.isBefore(sunset))
    }

    @Test
    fun `zone conversion preserves the instant`() {
        val date = LocalDate.of(2024, 3, 20)
        val utc = SunTimesCalculator.sunrise(date, 0.0, 0.0, ZoneOffset.UTC)
        val bangkok = SunTimesCalculator.sunrise(
            date, 0.0, 0.0, ZoneOffset.ofHours(7),
        )
        assertNotNull(utc)
        assertNotNull(bangkok)
        assertEquals(utc!!.toInstant(), bangkok!!.toInstant())
    }
}
