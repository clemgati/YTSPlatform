package com.yellowtrack.platform.feature.clients.presentation.list.mapper

import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.feature.clients.presentation.list.model.ClientSummary
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Builds the client list rows.
 *
 * Session counts are reached through projects, because a session belongs to a project and
 * a project belongs to a client. A wedding is one project but two sessions — the
 * engagement shoot and the day itself — and the list should say two.
 */
internal fun List<Client>.toClientSummaries(
    projects: List<Project>,
    sessions: List<Session>,
    now: Instant,
): List<ClientSummary> {
    val projectsByClient = projects.groupBy { it.clientId }
    val sessionsByProject = sessions.groupBy { it.projectId }

    return map { client ->
        val clientSessions =
            projectsByClient[client.id]
                .orEmpty()
                .flatMap { project -> sessionsByProject[project.id].orEmpty() }
                .filter { it.status != SessionStatus.Cancelled }

        val mostRecent =
            clientSessions
                .filter { it.startsAt <= now }
                .maxByOrNull(Session::startsAt)

        ClientSummary(
            id = client.id,
            displayName = client.displayName,
            initials = client.initials(),
            sessionCount = clientSessions.size,
            lastSession = mostRecent?.let { DateFormats.shortDate(it.startsAt, TimeZone.of(it.timeZoneId)) },
            tags = client.tags,
        )
    }
}

/**
 * Two letters for the avatar.
 *
 * Derived from the account name rather than a person's name, because an account may be a
 * couple ("Sarah & Michael Johnson") or a company ("Harborline Coffee"). The ampersand is
 * treated as a separator so a couple reads "SJ" rather than "S&".
 */
internal fun Client.initials(): String {
    val words = displayName.split(' ', '&', '-').filter(String::isNotBlank)

    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words.first().take(2).uppercase()
        else -> "${words.first().first()}${words.last().first()}".uppercase()
    }
}
