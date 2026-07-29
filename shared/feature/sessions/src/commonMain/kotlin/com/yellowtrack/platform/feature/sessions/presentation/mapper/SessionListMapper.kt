package com.yellowtrack.platform.feature.sessions.presentation.mapper

import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionGroup
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionListItem
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal fun buildSessionGroups(
    sessions: List<Session>,
    projects: List<Project>,
    clients: List<Client>,
    now: Instant,
    deviceZone: TimeZone,
): List<SessionGroup> {
    val projectsById = projects.associateBy { it.id }
    val clientsById = clients.associateBy { it.id }

    fun Session.toItem(): SessionListItem {
        val project = projectsById[projectId]
        val zone = TimeZone.of(timeZoneId)

        return SessionListItem(
            id = id,
            title = title,
            clientName = project?.let { clientsById[it.clientId]?.displayName }.orEmpty(),
            projectName = project?.name.orEmpty(),
            kind = kind,
            status = status,
            dayLabel = DateFormats.dayAndDate(startsAt, zone),
            timeRange = DateFormats.timeRange(startsAt, endsAt, zone),
            locationName = locationName,
            // Surfaced only when it differs from the device, so a photographer checking a
            // destination booking from home is not misled by their own clock.
            timeZoneNote = timeZoneId.takeIf { zone != deviceZone },
            zoneId = timeZoneId,
            editable = toEditableForm(zone),
        )
    }

    val upcoming = sessions.filter { it.startsAt >= now }.sortedBy(Session::startsAt)
    val past = sessions.filter { it.startsAt < now }.sortedByDescending(Session::startsAt)

    return buildList {
        if (upcoming.isNotEmpty()) add(SessionGroup("Upcoming", upcoming.map { it.toItem() }))
        if (past.isNotEmpty()) add(SessionGroup("Past", past.map { it.toItem() }))
    }
}

/** The session's own values, as the form holds them, read in the zone it happens in. */
private fun Session.toEditableForm(zone: TimeZone): NewSession {
    val start = startsAt.toLocalDateTime(zone)

    return NewSession(
        projectId = projectId,
        title = title,
        kind = kind,
        status = status,
        date = start.date.toString(),
        startTime = start.time.hourAndMinute(),
        endTime = endsAt.toLocalDateTime(zone).time.hourAndMinute(),
        callTime =
            callTime
                ?.toLocalDateTime(zone)
                ?.time
                ?.hourAndMinute()
                .orEmpty(),
        locationName = locationName.orEmpty(),
        locationAddress = locationAddress.orEmpty(),
        notes = notes.orEmpty(),
    )
}

/**
 * Formatted as the form expects to read it back.
 *
 * `LocalTime.toString` drops a zero minute field and appends seconds when they are not
 * zero, so a session stored with either would come back in a shape the form cannot parse.
 */
private fun LocalTime.hourAndMinute(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
