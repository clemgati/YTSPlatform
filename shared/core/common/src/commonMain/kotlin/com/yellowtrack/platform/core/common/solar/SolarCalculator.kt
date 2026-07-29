package com.yellowtrack.platform.core.common.solar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Sunrise, golden hour, and the sun's position, computed from a date and a coordinate.
 *
 * Implements the NOAA solar position algorithm, which is accurate to about a minute for
 * the latitudes anyone shoots at and degrades gracefully towards the poles. Nothing here
 * touches the network: a call sheet is written in an office and read in a field, and the
 * field is where the signal is not.
 *
 * All instants are absolute. Rendering them in the zone the shoot happens in is the
 * caller's business, which is the same rule `Session` follows.
 */
object SolarCalculator {
    /** The sun's height and bearing at one moment, seen from [at]. */
    fun positionAt(
        instant: Instant,
        at: GeoCoordinates,
    ): SolarPosition {
        val utc = instant.toLocalDateTime(TimeZone.UTC)
        val minutesFromMidnight =
            utc.hour * 60.0 + utc.minute + utc.second / 60.0
        val century = julianCentury(julianDay(utc.date) + minutesFromMidnight / MINUTES_IN_DAY)
        val solar = solarParameters(century)

        // True solar time: clock time corrected for the equation of time and for how far
        // the place sits from its meridian.
        val trueSolarMinutes =
            (minutesFromMidnight + solar.equationOfTime + 4.0 * at.longitude).mod(MINUTES_IN_DAY)
        val hourAngle = trueSolarMinutes / 4.0 - 180.0

        val cosZenith =
            sinDeg(at.latitude) * sinDeg(solar.declination) +
                cosDeg(at.latitude) * cosDeg(solar.declination) * cosDeg(hourAngle)
        val zenith = acosDeg(cosZenith.coerceIn(-1.0, 1.0))

        return SolarPosition(
            elevationDegrees = 90.0 - zenith,
            azimuthDegrees = azimuth(at.latitude, solar.declination, zenith, hourAngle),
        )
    }

    /** Every light window on [date] at [at]. */
    fun eventsOn(
        date: LocalDate,
        at: GeoCoordinates,
    ): SunEvents {
        val noonMinutes = solarNoonMinutes(date, at.longitude)
        val noon = date.atUtcMinutes(noonMinutes)

        fun rise(elevation: Double) = date.atUtcMinutes(eventMinutes(date, at, elevation, rising = true))

        fun set(elevation: Double) = date.atUtcMinutes(eventMinutes(date, at, elevation, rising = false))

        val goldenStartMorning = rise(GOLDEN_HOUR_LOWER)
        val goldenEndMorning = rise(GOLDEN_HOUR_UPPER)
        val goldenStartEvening = set(GOLDEN_HOUR_UPPER)
        val goldenEndEvening = set(GOLDEN_HOUR_LOWER)
        val dawn = rise(CIVIL_TWILIGHT)
        val dusk = set(CIVIL_TWILIGHT)

        return SunEvents(
            date = date,
            coordinates = at,
            solarNoon = noon,
            sunrise = rise(SUNRISE_ELEVATION),
            sunset = set(SUNRISE_ELEVATION),
            civilDawn = dawn,
            civilDusk = dusk,
            morningGoldenHour = window(goldenStartMorning, goldenEndMorning),
            eveningGoldenHour = window(goldenStartEvening, goldenEndEvening),
            morningBlueHour = window(dawn, goldenStartMorning),
            eveningBlueHour = window(goldenEndEvening, dusk),
            noonElevationDegrees = positionAt(noon, at).elevationDegrees,
        )
    }

    private fun window(
        start: Instant?,
        end: Instant?,
    ): SunWindow? = if (start != null && end != null && end > start) SunWindow(start, end) else null

    /**
     * Minutes from UTC midnight at which the sun reaches [elevation], or null if it never
     * does on this date.
     *
     * Refined once: the sun's declination moves through the day, so the parameters are
     * recomputed at the approximate event time and the answer taken again. Without this
     * the result drifts by a few minutes near the solstices, which is exactly when a
     * photographer is counting on the light.
     */
    private fun eventMinutes(
        date: LocalDate,
        at: GeoCoordinates,
        elevation: Double,
        rising: Boolean,
    ): Double? {
        val julianDay = julianDay(date)
        var estimate = solarNoonMinutes(date, at.longitude)

        repeat(REFINEMENT_PASSES) {
            val solar = solarParameters(julianCentury(julianDay + estimate / MINUTES_IN_DAY))
            val hourAngle = hourAngle(at.latitude, solar.declination, elevation) ?: return null
            val noon = 720.0 - 4.0 * at.longitude - solar.equationOfTime

            estimate = if (rising) noon - 4.0 * hourAngle else noon + 4.0 * hourAngle
        }

        return estimate
    }

    private fun solarNoonMinutes(
        date: LocalDate,
        longitude: Double,
    ): Double {
        val julianDay = julianDay(date)
        var noon = 720.0 - 4.0 * longitude

        repeat(REFINEMENT_PASSES) {
            val solar = solarParameters(julianCentury(julianDay + noon / MINUTES_IN_DAY))
            noon = 720.0 - 4.0 * longitude - solar.equationOfTime
        }

        return noon
    }

    /**
     * The hour angle at which the sun sits at [elevation], or null when it never does.
     *
     * A cosine outside -1..1 is not an error: it means the sun stays above or below that
     * elevation for the whole day, which is what happens every summer inside the Arctic
     * circle and every winter's day in a deep enough polar night.
     */
    private fun hourAngle(
        latitude: Double,
        declination: Double,
        elevation: Double,
    ): Double? {
        val cosHourAngle =
            (cosDeg(90.0 - elevation) - sinDeg(latitude) * sinDeg(declination)) /
                (cosDeg(latitude) * cosDeg(declination))

        return if (cosHourAngle in -1.0..1.0) acosDeg(cosHourAngle) else null
    }

    private fun azimuth(
        latitude: Double,
        declination: Double,
        zenith: Double,
        hourAngle: Double,
    ): Double {
        val denominator = cosDeg(latitude) * sinDeg(zenith)
        // Straight overhead or at a pole, every direction is the same direction.
        if (denominator == 0.0) return 0.0

        val cosAzimuth =
            ((sinDeg(latitude) * cosDeg(zenith)) - sinDeg(declination)) / denominator
        val angle = acosDeg(cosAzimuth.coerceIn(-1.0, 1.0))

        return if (hourAngle > 0) (angle + 180.0).mod(360.0) else (540.0 - angle).mod(360.0)
    }

    private data class SolarParameters(
        /** How far north or south of the equator the sun is, in degrees. */
        val declination: Double,
        /** How far a sundial runs from the clock on this date, in minutes. */
        val equationOfTime: Double,
    )

    private fun solarParameters(century: Double): SolarParameters {
        val meanLongitude = (280.46646 + century * (36000.76983 + century * 0.0003032)).mod(360.0)
        val meanAnomaly = 357.52911 + century * (35999.05029 - 0.0001537 * century)
        val eccentricity = 0.016708634 - century * (0.000042037 + 0.0000001267 * century)

        val centre =
            sinDeg(meanAnomaly) * (1.914602 - century * (0.004817 + 0.000014 * century)) +
                sinDeg(2 * meanAnomaly) * (0.019993 - 0.000101 * century) +
                sinDeg(3 * meanAnomaly) * 0.000289

        val omega = 125.04 - 1934.136 * century
        val apparentLongitude = meanLongitude + centre - 0.00569 - 0.00478 * sinDeg(omega)

        val meanObliquity =
            23.0 + (26.0 + (21.448 - century * (46.815 + century * (0.00059 - century * 0.001813))) / 60.0) / 60.0
        val obliquity = meanObliquity + 0.00256 * cosDeg(omega)

        val y = tanDeg(obliquity / 2.0).pow(2)
        // The bracket is in radians and the result is wanted in minutes of time, hence
        // four minutes per degree. Dividing here rather than multiplying makes the whole
        // correction about three thousand times too small, which reads as a plausible
        // sunrise a few minutes out — and only in the months where the correction is
        // large enough to notice.
        val equationOfTime =
            4.0 * (
                y * sinDeg(2 * meanLongitude) -
                    2 * eccentricity * sinDeg(meanAnomaly) +
                    4 * eccentricity * y * sinDeg(meanAnomaly) * cosDeg(2 * meanLongitude) -
                    0.5 * y * y * sinDeg(4 * meanLongitude) -
                    1.25 * eccentricity * eccentricity * sinDeg(2 * meanAnomaly)
            ) * DEGREES_IN_RADIAN

        return SolarParameters(
            declination = asinDeg(sinDeg(obliquity) * sinDeg(apparentLongitude)),
            equationOfTime = equationOfTime,
        )
    }

    /** Days since noon on 1 January 4713 BC, at midnight UT on [date]. */
    private fun julianDay(date: LocalDate): Double {
        var year = date.year
        var month = date.month.number
        val day = date.day

        if (month <= 2) {
            year -= 1
            month += 12
        }

        val centuryPart = floor(year / 100.0)
        val gregorian = 2 - centuryPart + floor(centuryPart / 4.0)

        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + gregorian - 1524.5
    }

    private fun julianCentury(julianDay: Double): Double = (julianDay - J2000) / DAYS_IN_CENTURY

    private fun LocalDate.atUtcMinutes(minutes: Double?): Instant? =
        minutes?.let { atStartOfDayIn(TimeZone.UTC) + it.minutes }

    private fun LocalDate.atUtcMinutes(minutes: Double): Instant = atStartOfDayIn(TimeZone.UTC) + minutes.minutes

    private const val J2000 = 2_451_545.0

    private const val DAYS_IN_CENTURY = 36_525.0

    private const val MINUTES_IN_DAY = 1_440.0

    /**
     * Enough to converge. The first pass lands within a few minutes and the second within
     * seconds; a third changes nothing a photographer could act on.
     */
    private const val REFINEMENT_PASSES = 2
}

private const val DEGREES_IN_RADIAN = 180.0 / PI

private fun sinDeg(degrees: Double): Double = sin(degrees / DEGREES_IN_RADIAN)

private fun cosDeg(degrees: Double): Double = cos(degrees / DEGREES_IN_RADIAN)

private fun tanDeg(degrees: Double): Double = tan(degrees / DEGREES_IN_RADIAN)

private fun asinDeg(value: Double): Double = asin(value) * DEGREES_IN_RADIAN

private fun acosDeg(value: Double): Double = acos(value) * DEGREES_IN_RADIAN
