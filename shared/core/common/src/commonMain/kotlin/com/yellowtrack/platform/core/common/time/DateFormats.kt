package com.yellowtrack.platform.core.common.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Display formatting for dates and times.
 *
 * Hand-rolled and English-only, because Compose Multiplatform has no shared locale-aware
 * formatter across Android, iOS, desktop, and wasm. Localisation will need per-platform
 * formatters; until then these keep formatting in one place rather than scattered through
 * feature mappers.
 *
 * Every function takes the zone explicitly. A session's zone is a property of the session,
 * not of the device reading it — a destination wedding is at 4pm where it happens,
 * regardless of where the photographer is sitting when they check the schedule.
 */
object DateFormats {
    /** "Jul 21" */
    fun shortDate(
        instant: Instant,
        zone: TimeZone,
    ): String =
        with(instant.toLocalDateTime(zone)) {
            "${month.shortLabel} $day"
        }

    /**
     * "Jul 21" for a date that carries no time of day.
     *
     * A cost is incurred on a day, not at an instant, so it has no zone to be read in and
     * none is asked for. Formatting it through an `Instant` would invent one.
     */
    fun shortDate(date: LocalDate): String = "${date.month.shortLabel} ${date.day}"

    /** "July 21, 2026" */
    fun fullDate(
        instant: Instant,
        zone: TimeZone,
    ): String =
        with(instant.toLocalDateTime(zone)) {
            "${month.longLabel} $day, $year"
        }

    /** "Friday, July 31" */
    fun dayAndDate(
        instant: Instant,
        zone: TimeZone,
    ): String =
        with(instant.toLocalDateTime(zone)) {
            "${dayOfWeek.longLabel}, ${month.longLabel} $day"
        }

    /** "6:00 PM" */
    fun timeOfDay(
        instant: Instant,
        zone: TimeZone,
    ): String = instant.toLocalDateTime(zone).timeOfDay()

    /** "10:00 AM – 8:00 PM" */
    fun timeRange(
        start: Instant,
        end: Instant,
        zone: TimeZone,
    ): String = "${timeOfDay(start, zone)} – ${timeOfDay(end, zone)}"

    private fun LocalDateTime.timeOfDay(): String {
        val suffix = if (hour < 12) "AM" else "PM"
        val displayHour =
            when (hour % 12) {
                0 -> 12
                else -> hour % 12
            }
        return "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
    }
}

private val Month.shortLabel: String
    get() = longLabel.take(3)

private val Month.longLabel: String
    get() =
        when (this) {
            Month.JANUARY -> "January"
            Month.FEBRUARY -> "February"
            Month.MARCH -> "March"
            Month.APRIL -> "April"
            Month.MAY -> "May"
            Month.JUNE -> "June"
            Month.JULY -> "July"
            Month.AUGUST -> "August"
            Month.SEPTEMBER -> "September"
            Month.OCTOBER -> "October"
            Month.NOVEMBER -> "November"
            Month.DECEMBER -> "December"
        }

private val DayOfWeek.longLabel: String
    get() =
        when (this) {
            DayOfWeek.MONDAY -> "Monday"
            DayOfWeek.TUESDAY -> "Tuesday"
            DayOfWeek.WEDNESDAY -> "Wednesday"
            DayOfWeek.THURSDAY -> "Thursday"
            DayOfWeek.FRIDAY -> "Friday"
            DayOfWeek.SATURDAY -> "Saturday"
            DayOfWeek.SUNDAY -> "Sunday"
        }
