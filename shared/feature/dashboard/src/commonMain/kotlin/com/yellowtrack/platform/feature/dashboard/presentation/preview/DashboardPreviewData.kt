package com.yellowtrack.platform.feature.dashboard.presentation.preview

import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardUiState
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardClient
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardEnquiry
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSession
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatus
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatusItem
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSummary
import com.yellowtrack.platform.feature.dashboard.presentation.model.EnquiryOutcomesSummary

internal object DashboardPreviewData {
    val summary =
        DashboardSummary(
            todaysSessions =
                listOf(
                    DashboardSession(
                        id = SessionId("session-1"),
                        clientName = "John Smith",
                        title = "Professional Headshots",
                        time = "10:00 AM",
                    ),
                    DashboardSession(
                        id = SessionId("session-2"),
                        clientName = "Sarah Johnson",
                        title = "Branding Session",
                        time = "2:30 PM",
                    ),
                ),
            // Two the studio has answered and one it has not, so the preview shows the
            // case the section exists for: an enquiry that has left the urgent list.
            allEnquiries =
                listOf(
                    DashboardEnquiry(
                        id = LeadId("lead-1"),
                        name = "Priya & Tom",
                        source = "Client referral",
                        waitingLabel = "waiting 2 days",
                        isUrgent = true,
                        statusLabel = "Awaiting a reply",
                        canConvert = true,
                    ),
                    DashboardEnquiry(
                        id = LeadId("lead-2"),
                        name = "Harbourline Studios",
                        source = "Instagram",
                        waitingLabel = "",
                        isUrgent = false,
                        statusLabel = "Won",
                        convertedLabel = "Became a client",
                    ),
                    DashboardEnquiry(
                        id = LeadId("lead-3"),
                        name = "cheap seo services",
                        source = "Website",
                        waitingLabel = "",
                        isUrgent = false,
                        statusLabel = "Replied",
                    ),
                ),
            outcomes =
                EnquiryOutcomesSummary(
                    headline = "62% of settled enquiries became clients",
                    detail = "8 became clients, 5 went elsewhere, 3 still open.",
                ),
            recentClients =
                listOf(
                    DashboardClient(
                        id = ClientId("client-1"),
                        name = "Emily Davis",
                    ),
                    DashboardClient(
                        id = ClientId("client-2"),
                        name = "Michael Brown",
                    ),
                    DashboardClient(
                        id = ClientId("client-3"),
                        name = "Jane Doe",
                    ),
                ),
            studioStatus =
                DashboardStudioStatus(
                    items =
                        listOf(
                            DashboardStudioStatusItem(
                                title = "Cameras ready",
                                ready = true,
                            ),
                            DashboardStudioStatusItem(
                                title = "Batteries charged",
                                ready = true,
                            ),
                            DashboardStudioStatusItem(
                                title = "Memory cards formatted",
                                ready = true,
                            ),
                            DashboardStudioStatusItem(
                                title = "Backdrop installed",
                                ready = false,
                            ),
                        ),
                ),
        )

    val successState =
        DashboardUiState(
            summary = UiState.Success(summary),
        )

    val loadingState =
        DashboardUiState(
            summary = UiState.Loading,
        )

    val emptyState =
        DashboardUiState(
            summary = UiState.Empty,
        )

    val errorState =
        DashboardUiState(
            summary =
                UiState.Error(
                    message = "Dashboard data could not be loaded.",
                ),
        )
}
