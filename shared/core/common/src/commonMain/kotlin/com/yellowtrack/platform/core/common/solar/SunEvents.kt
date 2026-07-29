package com.yellowtrack.platform.core.common.solar

import kotlinx.datetime.LocalDate
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A stretch of usable light.
 *
 * Held as a pair of instants rather than a start and a length, because the end is the part
 * a photographer is watching — the light does not run over.
 */
data class SunWindow(
    val start: Instant,
    val end: Instant,
) {
    val duration: Duration get() = end - start
}

/**
 * Where the sun is on a given day at a given place.
 *
 * Every field is computed, never fetched: latitude, longitude, and a date are enough, so
 * this works on a moor with no signal, which is exactly where a shoot needs it.
 *
 * The nullable fields are not failures. Above the Arctic and Antarctic circles the sun may
 * not cross a given elevation at all, and a window that never opens is honestly absent
 * rather than invented. [isPolarDay] and [isPolarNight] say which case a missing sunrise
 * is.
 */
data class SunEvents(
    val date: LocalDate,
    val coordinates: GeoCoordinates,
    /** When the sun is highest. Always defined, even where it never rises. */
    val solarNoon: Instant,
    val sunrise: Instant?,
    val sunset: Instant?,
    /** Sun 6° below the horizon: the limit of usable ambient light. */
    val civilDawn: Instant?,
    val civilDusk: Instant?,
    /** Sun between -4° and 6°: low, warm, directional light. */
    val morningGoldenHour: SunWindow?,
    val eveningGoldenHour: SunWindow?,
    /** Sun between -6° and -4°: even, cool light, after sunset and before dawn. */
    val morningBlueHour: SunWindow?,
    val eveningBlueHour: SunWindow?,
    /** The sun's height at [solarNoon], which decides how harsh the middle of the day is. */
    val noonElevationDegrees: Double,
) {
    /** The sun stays up all day — a summer's day inside the Arctic circle. */
    val isPolarDay: Boolean get() = sunrise == null && noonElevationDegrees > 0

    /** The sun never rises. */
    val isPolarNight: Boolean get() = sunrise == null && noonElevationDegrees <= 0

    /** How long the sun is above the horizon, or null where it never rises or never sets. */
    val daylight: Duration?
        get() = if (sunrise != null && sunset != null) sunset - sunrise else null
}

/**
 * Where the sun is in the sky at one moment.
 *
 * [azimuthDegrees] is measured clockwise from true north, so 90 is due east and 270 due
 * west. It is the figure that answers the question a photographer actually asks on a
 * recce — which way will the light be coming from, and what will be backlit.
 */
data class SolarPosition(
    val elevationDegrees: Double,
    val azimuthDegrees: Double,
) {
    val isUp: Boolean get() = elevationDegrees > 0

    /** Low, warm, directional light: the sun between -4° and 6°. */
    val isGoldenHour: Boolean get() = elevationDegrees in GOLDEN_HOUR_LOWER..GOLDEN_HOUR_UPPER

    /** The even light after sunset and before dawn: the sun between -6° and -4°. */
    val isBlueHour: Boolean get() = elevationDegrees in CIVIL_TWILIGHT..GOLDEN_HOUR_LOWER

    /** Overhead enough that the light is hard and the shadows are short. */
    val isHarsh: Boolean get() = elevationDegrees > HARSH_LIGHT_ELEVATION
}

/**
 * The horizon, allowing for refraction and the sun's own width.
 *
 * Sunrise is defined as the upper limb touching the horizon, not the centre, and the
 * atmosphere bends the light by roughly another third of a degree.
 */
internal const val SUNRISE_ELEVATION = -0.833

internal const val CIVIL_TWILIGHT = -6.0

internal const val GOLDEN_HOUR_LOWER = -4.0

internal const val GOLDEN_HOUR_UPPER = 6.0

private const val HARSH_LIGHT_ELEVATION = 50.0
