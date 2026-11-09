package com.chantingscheduler.app.scheduling

import com.chantingscheduler.app.data.LatLng
import org.shredzone.commons.suncalc.SunTimes
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Thin wrapper around commons-suncalc for sunrise/sunset instants. The suncalc
 * parameters pin the computation timezone explicitly — otherwise results
 * silently depend on the host/JVM default zone, which breaks unit tests and
 * would compute wrong sun times for any non-default zone.
 */
object SunTimesCalculator {

    /** Local sunrise on [date] at [location], or null (polar day/night). */
    fun sunrise(
        date: LocalDate,
        location: LatLng,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ZonedDateTime? = sunrise(date, location.latitude, location.longitude, zone)

    /** Local sunset on [date] at [location], or null (polar day/night). */
    fun sunset(
        date: LocalDate,
        location: LatLng,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ZonedDateTime? = sunset(date, location.latitude, location.longitude, zone)

    fun sunrise(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ZonedDateTime? = compute(date, latitude, longitude, zone).rise?.withZoneSameInstant(zone)

    fun sunset(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ZonedDateTime? = compute(date, latitude, longitude, zone).set?.withZoneSameInstant(zone)

    private fun compute(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SunTimes =
        SunTimes.compute()
            .on(date.year, date.monthValue, date.dayOfMonth)
            .at(latitude, longitude)
            .timezone(TimeZone.getTimeZone(zone))
            .execute()
}
