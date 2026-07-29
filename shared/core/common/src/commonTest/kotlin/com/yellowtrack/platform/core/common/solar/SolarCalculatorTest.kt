package com.yellowtrack.platform.core.common.solar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Checked against published almanac times rather than against itself.
 *
 * A solar calculator that agrees with its own output proves nothing, so every expected
 * time here comes from an independent source, and the tolerance is stated rather than
 * widened until the test passes: the NOAA algorithm is good to about a minute at these
 * latitudes, so two is a fair bound and a failure outside it is a real defect.
 */
class SolarCalculatorTest {
    private val london = GeoCoordinates(latitude = 51.5074, longitude = -0.1278)
    private val sydney = GeoCoordinates(latitude = -33.8688, longitude = 151.2093)
    private val tromso = GeoCoordinates(latitude = 69.6496, longitude = 18.9560)
    private val quito = GeoCoordinates(latitude = -0.1807, longitude = -78.4678)

    private fun utc(
        date: String,
        time: String,
    ): Instant = LocalDateTime.parse("${date}T$time").toInstant(TimeZone.UTC)

    private fun assertCloseTo(
        expected: Instant,
        actual: Instant?,
        what: String,
    ) {
        val found = assertNotNull(actual, "$what was not computed at all")
        val drift = abs((found - expected).inWholeSeconds)

        assertTrue(
            drift <= TOLERANCE_SECONDS,
            "$what was ${found.toLocalDateTime(TimeZone.UTC)}, expected about " +
                "${expected.toLocalDateTime(TimeZone.UTC)} (out by ${drift / 60.0} minutes)",
        )
    }

    // --- Against the almanac -----------------------------------------------------------

    @Test
    fun `London on the summer solstice`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 6, 21), london)

        // 04:43 and 21:21 British Summer Time, which is UTC+1.
        assertCloseTo(utc("2026-06-21", "03:43"), events.sunrise, "sunrise")
        assertCloseTo(utc("2026-06-21", "20:21"), events.sunset, "sunset")
    }

    @Test
    fun `London on the winter solstice`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 12, 21), london)

        assertCloseTo(utc("2026-12-21", "08:03"), events.sunrise, "sunrise")
        assertCloseTo(utc("2026-12-21", "15:53"), events.sunset, "sunset")
    }

    @Test
    fun `Sydney in the southern summer`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 1, 15), sydney)

        // 06:00 and 20:07 local, which is UTC+11 in January.
        assertCloseTo(utc("2026-01-14", "19:00"), events.sunrise, "sunrise")
        assertCloseTo(utc("2026-01-15", "09:07"), events.sunset, "sunset")
    }

    @Test
    fun `the equator gets about twelve hours of daylight all year`() {
        val march = assertNotNull(SolarCalculator.eventsOn(LocalDate(2026, 3, 20), quito).daylight)
        val september = assertNotNull(SolarCalculator.eventsOn(LocalDate(2026, 9, 22), quito).daylight)

        assertTrue(abs(march.inWholeMinutes - 720) <= 15, "equinox daylight at the equator was $march")
        assertTrue(abs(september.inWholeMinutes - 720) <= 15, "equinox daylight at the equator was $september")
    }

    // --- The poles ---------------------------------------------------------------------

    @Test
    fun `the midnight sun is a day with no sunrise rather than a failure`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 6, 21), tromso)

        assertNull(events.sunrise, "the sun does not rise in Tromso in June because it never sets")
        assertNull(events.sunset)
        assertTrue(events.isPolarDay)
        assertFalse(events.isPolarNight)
        assertTrue(events.noonElevationDegrees > 0)
    }

    @Test
    fun `polar night is told apart from midnight sun`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 12, 21), tromso)

        assertNull(events.sunrise)
        assertTrue(events.isPolarNight)
        assertFalse(events.isPolarDay)
        assertTrue(events.noonElevationDegrees < 0)
    }

    // --- Golden hour -------------------------------------------------------------------

    @Test
    fun `golden hour brackets sunrise and sunset`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 6, 21), london)

        val sunrise = assertNotNull(events.sunrise)
        val sunset = assertNotNull(events.sunset)
        val morning = assertNotNull(events.morningGoldenHour)
        val evening = assertNotNull(events.eveningGoldenHour)

        assertTrue(morning.start < sunrise, "the morning window opens before the sun is up")
        assertTrue(morning.end > sunrise, "and closes once the sun has climbed")
        assertTrue(evening.start < sunset, "the evening window opens while the sun is still up")
        assertTrue(evening.end > sunset, "and closes after it has gone")
    }

    @Test
    fun `golden hour is short in the tropics and long in the north`() {
        val date = LocalDate(2026, 6, 21)
        val equator = assertNotNull(SolarCalculator.eventsOn(date, quito).eveningGoldenHour)
        val northern = assertNotNull(SolarCalculator.eventsOn(date, london).eveningGoldenHour)

        assertTrue(
            northern.duration > equator.duration,
            "the sun sets steeply at the equator and obliquely in the north; " +
                "London was ${northern.duration}, Quito ${equator.duration}",
        )
        assertTrue(equator.duration.inWholeMinutes in 20..45, "equatorial golden hour was ${equator.duration}")
    }

    @Test
    fun `blue hour sits between civil dawn and golden hour`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 6, 21), london)

        val blue = assertNotNull(events.morningBlueHour)
        val golden = assertNotNull(events.morningGoldenHour)

        assertEquals(events.civilDawn, blue.start)
        assertEquals(golden.start, blue.end, "one window ends exactly where the next begins")
    }

    // --- Position ----------------------------------------------------------------------

    @Test
    fun `the sun is highest at solar noon`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 6, 21), london)
        val atNoon = SolarCalculator.positionAt(events.solarNoon, london)

        val hourBefore = SolarCalculator.positionAt(events.solarNoon - kotlin.time.Duration.parse("1h"), london)
        val hourAfter = SolarCalculator.positionAt(events.solarNoon + kotlin.time.Duration.parse("1h"), london)

        assertTrue(atNoon.elevationDegrees > hourBefore.elevationDegrees)
        assertTrue(atNoon.elevationDegrees > hourAfter.elevationDegrees)

        // London's noon sun on the solstice reaches roughly 62 degrees.
        assertTrue(
            abs(atNoon.elevationDegrees - 62.0) < 1.0,
            "noon elevation was ${atNoon.elevationDegrees}",
        )
    }

    @Test
    fun `the sun is due south at noon in the northern hemisphere and due north in the southern`() {
        val northern =
            SolarCalculator.positionAt(
                SolarCalculator.eventsOn(LocalDate(2026, 6, 21), london).solarNoon,
                london,
            )
        val southern =
            SolarCalculator.positionAt(
                SolarCalculator.eventsOn(LocalDate(2026, 6, 21), sydney).solarNoon,
                sydney,
            )

        assertTrue(abs(northern.azimuthDegrees - 180.0) < 1.0, "London noon azimuth was ${northern.azimuthDegrees}")
        assertTrue(
            southern.azimuthDegrees < 1.0 || southern.azimuthDegrees > 359.0,
            "Sydney noon azimuth was ${southern.azimuthDegrees}",
        )
    }

    @Test
    fun `the sun rises in the east and sets in the west`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 3, 20), london)

        val atSunrise = SolarCalculator.positionAt(assertNotNull(events.sunrise), london)
        val atSunset = SolarCalculator.positionAt(assertNotNull(events.sunset), london)

        assertTrue(abs(atSunrise.azimuthDegrees - 90.0) < 5.0, "sunrise azimuth was ${atSunrise.azimuthDegrees}")
        assertTrue(abs(atSunset.azimuthDegrees - 270.0) < 5.0, "sunset azimuth was ${atSunset.azimuthDegrees}")
    }

    @Test
    fun `the elevation at sunrise is the horizon allowing for refraction`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 6, 21), london)
        val atSunrise = SolarCalculator.positionAt(assertNotNull(events.sunrise), london)

        assertTrue(
            abs(atSunrise.elevationDegrees - SUNRISE_ELEVATION) < 0.1,
            "elevation at the computed sunrise was ${atSunrise.elevationDegrees}",
        )
    }

    @Test
    fun `a position inside the golden hour window says so`() {
        val events = SolarCalculator.eventsOn(LocalDate(2026, 6, 21), london)
        val window = assertNotNull(events.eveningGoldenHour)
        val midway = window.start + (window.duration / 2)

        assertTrue(SolarCalculator.positionAt(midway, london).isGoldenHour)
        assertFalse(SolarCalculator.positionAt(events.solarNoon, london).isGoldenHour)
    }

    // --- The equation of time ----------------------------------------------------------

    @Test
    fun `solar noon runs ahead of and behind clock noon through the year`() {
        val greenwich = GeoCoordinates(latitude = 51.4779, longitude = 0.0)

        // A sundial at Greenwich reads about sixteen minutes fast in early November and
        // about fourteen minutes slow in mid-February. This is the equation of time, and
        // it is the one term in the algorithm that a wrong unit conversion hides in: it is
        // near zero around the June solstice, so a sunrise checked only in summer looks
        // right while every other month is minutes out.
        val november = SolarCalculator.eventsOn(LocalDate(2026, 11, 3), greenwich).solarNoon
        val february = SolarCalculator.eventsOn(LocalDate(2026, 2, 11), greenwich).solarNoon

        assertCloseTo(utc("2026-11-03", "11:43:30"), november, "November solar noon")
        assertCloseTo(utc("2026-02-11", "12:14:14"), february, "February solar noon")
    }

    // --- Coordinates -------------------------------------------------------------------

    @Test
    fun `coordinates outside the earth are refused`() {
        val transposed = runCatching { GeoCoordinates(latitude = 151.2093, longitude = -33.8688) }

        assertTrue(
            transposed.isFailure,
            "a transposed pair must be refused rather than produce a plausible-looking sunset",
        )
    }

    private companion object {
        /** The algorithm is good to about a minute at these latitudes. */
        const val TOLERANCE_SECONDS = 120L
    }
}
