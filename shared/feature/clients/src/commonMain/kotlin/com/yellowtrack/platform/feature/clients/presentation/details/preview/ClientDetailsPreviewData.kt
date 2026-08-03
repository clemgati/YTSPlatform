package com.yellowtrack.platform.feature.clients.presentation.details.preview

import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsUiState
import com.yellowtrack.platform.feature.clients.presentation.details.model.BookingSummary
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientContact
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientRemoval
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientSessionHistoryItem
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientUpcomingSession
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
import com.yellowtrack.platform.feature.clients.presentation.model.NewClient

internal object ClientDetailsPreviewData {
    val client =
        ClientDetailsModel(
            id = ClientId("client-1"),
            displayName = "John Smith",
            initials = "JS",
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
            bookings =
                listOf(
                    BookingSummary(
                        id = ProjectId.new(),
                        name = "Smith Creative — Brand Refresh",
                        serviceLine = "Branding",
                        status = ProjectStatus.Booked,
                        value = "$4,500.00",
                        bookedLabel = "since 12 Mar 2026",
                        editable =
                            NewProject(
                                name = "Smith Creative — Brand Refresh",
                                serviceLine = ServiceLine.Branding,
                                status = ProjectStatus.Booked,
                                contractValue = "4500.00",
                                notes = "",
                            ),
                    ),
                ),
            // One booking, so the preview shows the held case and its explanation — the
            // state worth looking at, since the available one is just a button.
            removal = ClientRemoval.HeldByBookings(count = 1),
            editable =
                NewClient(
                    accountName = "Sarah & Michael Johnson",
                    accountType = ClientAccountType.Couple,
                    contactFirstName = "Sarah",
                    contactLastName = "Johnson",
                    company = "",
                    email = "sarah@example.com",
                    phone = "07700 900123",
                    notes = "Prefers warm color grading.",
                ),
        )

    val successState =
        ClientDetailsUiState(
            client = UiState.Success(client),
        )

    val loadingState =
        ClientDetailsUiState(
            client = UiState.Loading,
        )

    val emptyState =
        ClientDetailsUiState(
            client = UiState.Empty,
        )

    val errorState =
        ClientDetailsUiState(
            client =
                UiState.Error(
                    message = "Client details could not be loaded.",
                ),
        )

    val clientWithoutUpcomingSession =
        client.copy(
            upcomingSession = null,
        )

    val clientWithoutHistory =
        client.copy(
            sessionHistory = emptyList(),
        )

    val clientWithoutNotes =
        client.copy(
            notes = emptyList(),
        )
}
