package com.yellowtrack.platform.feature.sessions.presentation.model

import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Instant

/** A booking a session can be scheduled inside. */
internal data class BookingOption(
    val id: ProjectId,
    val label: String,
)

/**
 * What the session form collected.
 *
 * Times stay as text; turning them into instants is [timing]'s job, so the rule about what
 * a typed time means lives in one place and the form and the ViewModel cannot disagree
 * about it.
 */
internal data class NewSession(
    val projectId: ProjectId,
    val title: String,
    val kind: SessionKind,
    val status: SessionStatus,
    val date: String,
    val startTime: String,
    val endTime: String,
    /** Blank when nobody is being called earlier than the start. */
    val callTime: String,
    val locationName: String,
    val locationAddress: String,
    val notes: String,
)

/** A session's instants, resolved in the zone it happens in. */
internal data class SessionTiming(
    val startsAt: Instant,
    val endsAt: Instant,
    val callTime: Instant?,
) {
    val duration: Duration get() = endsAt - startsAt
}

/**
 * Resolves the typed date and times, or null if any of them will not parse.
 *
 * An end time at or before the start is read as the following morning rather than
 * rejected. A wedding that starts at 14:00 and finishes at 01:00 is an ordinary booking,
 * and a form that refuses it would make the commonest job in the business unenterable.
 * The duration is shown back on the form so a genuine typo still announces itself as an
 * implausible number of hours.
 *
 * The call time is resolved against the start rather than the end, and rolls with it, so
 * crew called at 12:00 for a 14:00 start are called on the shoot's own day.
 */
internal fun NewSession.timing(zone: TimeZone): SessionTiming? {
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    val start = runCatching { LocalTime.parse(startTime) }.getOrNull() ?: return null
    val end = runCatching { LocalTime.parse(endTime) }.getOrNull() ?: return null

    val startsAt = LocalDateTime(day, start).toInstant(zone)
    val endDay = if (end > start) day else day.plus(1, DateTimeUnit.DAY)
    val endsAt = LocalDateTime(endDay, end).toInstant(zone)

    val call =
        when {
            callTime.isBlank() -> null
            else -> {
                val time = runCatching { LocalTime.parse(callTime) }.getOrNull() ?: return null
                LocalDateTime(day, time).toInstant(zone)
            }
        }

    return SessionTiming(startsAt = startsAt, endsAt = endsAt, callTime = call)
}
