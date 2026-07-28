package com.yellowtrack.platform.feature.dashboard.presentation.preview

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardUiState
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardClient
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSession
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatus
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatusItem
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSummary

internal object DashboardPreviewData {
    val summary =
        DashboardSummary(
            todaysSessions =
                listOf(
                    DashboardSession(
                        clientName = "John Smith",
                        title = "Professional Headshots",
                        time = "10:00 AM",
                    ),
                    DashboardSession(
                        clientName = "Sarah Johnson",
                        title = "Branding Session",
                        time = "2:30 PM",
                    ),
                ),
            recentClients =
                listOf(
                    DashboardClient(
                        name = "Emily Davis",
                    ),
                    DashboardClient(
                        name = "Michael Brown",
                    ),
                    DashboardClient(
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
