package com.yellowtrack.platform.feature.sessions.presentation.mapper

import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionGroup
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionListItem
import kotlinx.datetime.TimeZone
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
        )
    }

    val upcoming = sessions.filter { it.startsAt >= now }.sortedBy(Session::startsAt)
    val past = sessions.filter { it.startsAt < now }.sortedByDescending(Session::startsAt)

    return buildList {
        if (upcoming.isNotEmpty()) add(SessionGroup("Upcoming", upcoming.map { it.toItem() }))
        if (past.isNotEmpty()) add(SessionGroup("Past", past.map { it.toItem() }))
    }
}
