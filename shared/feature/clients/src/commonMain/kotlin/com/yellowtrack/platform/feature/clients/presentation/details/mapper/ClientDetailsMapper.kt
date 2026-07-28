package com.yellowtrack.platform.feature.clients.presentation.details.mapper

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientContact
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientSessionHistoryItem
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientUpcomingSession

internal fun Client.toClientDetailsModel(): ClientDetailsModel {
    val metadata = sampleDetailsMetadata()

    return ClientDetailsModel(
        id = id,
        displayName = displayName,
        initials =
            buildString {
                firstName.firstOrNull()?.let(::append)
                lastName.firstOrNull()?.let(::append)
            }.uppercase(),
        tags = metadata.tags,
        contact = metadata.contact,
        upcomingSession = metadata.upcomingSession,
        sessionHistory = metadata.sessionHistory,
        notes = metadata.notes,
    )
}

private fun Client.sampleDetailsMetadata(): SampleClientDetailsMetadata =
    when (id.value) {
        "client-1" ->
            SampleClientDetailsMetadata(
                tags =
                    listOf(
                        "Portrait",
                        "Branding",
                    ),
                contact =
                    ClientContact(
                        phone = "(602) 555-1234",
                        email = "john.smith@example.com",
                        instagram = "@johnsmith",
                        company = "Smith Creative",
                    ),
                upcomingSession =
                    ClientUpcomingSession(
                        title = "Professional Headshots",
                        date = "Friday, July 31",
                        time = "6:00 PM",
                        location = "Yellow Track Studios",
                    ),
                sessionHistory =
                    listOf(
                        ClientSessionHistoryItem(
                            title = "Branding Session",
                            date = "July 21, 2026",
                        ),
                        ClientSessionHistoryItem(
                            title = "Family Portraits",
                            date = "May 18, 2026",
                        ),
                        ClientSessionHistoryItem(
                            title = "Professional Headshots",
                            date = "March 7, 2026",
                        ),
                    ),
                notes =
                    listOf(
                        "Prefers warm color grading.",
                        "Likes natural posing and candid expressions.",
                        "Needs vertical crops for social media.",
                    ),
            )

        "client-2" ->
            SampleClientDetailsMetadata(
                tags = listOf("Headshots"),
                contact =
                    ClientContact(
                        phone = "(623) 555-0198",
                        email = "sarah.johnson@example.com",
                        instagram = "@sarahjohnson",
                        company = null,
                    ),
                upcomingSession = null,
                sessionHistory =
                    listOf(
                        ClientSessionHistoryItem(
                            title = "Corporate Headshots",
                            date = "June 11, 2026",
                        ),
                    ),
                notes =
                    listOf(
                        "Prefers simple neutral backgrounds.",
                    ),
            )

        else ->
            SampleClientDetailsMetadata(
                tags = emptyList(),
                contact =
                    ClientContact(
                        phone = null,
                        email = null,
                        instagram = null,
                        company = null,
                    ),
                upcomingSession = null,
                sessionHistory = emptyList(),
                notes = emptyList(),
            )
    }

private data class SampleClientDetailsMetadata(
    val tags: List<String>,
    val contact: ClientContact,
    val upcomingSession: ClientUpcomingSession?,
    val sessionHistory: List<ClientSessionHistoryItem>,
    val notes: List<String>,
)
