package com.yellowtrack.platform.feature.clients.presentation.list.preview

import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsUiState
import com.yellowtrack.platform.feature.clients.presentation.list.model.ClientSummary

internal object ClientsPreviewData {
    val johnSmith =
        ClientSummary(
            id = ClientId("client-1"),
            displayName = "John Smith",
            initials = "JS",
            sessionCount = 12,
            lastSession = "Jul 21",
            tags =
                listOf(
                    "Portrait",
                    "Branding",
                ),
        )

    val sarahJohnson =
        ClientSummary(
            id = ClientId("client-2"),
            displayName = "Sarah Johnson",
            initials = "SJ",
            sessionCount = 4,
            lastSession = "Jun 11",
            tags =
                listOf(
                    "Headshots",
                ),
        )

    val emilyDavis =
        ClientSummary(
            id = ClientId("client-3"),
            displayName = "Emily Davis",
            initials = "ED",
            sessionCount = 8,
            lastSession = "Jul 4",
            tags =
                listOf(
                    "Family",
                    "Portrait",
                ),
        )

    val michaelBrown =
        ClientSummary(
            id = ClientId("client-4"),
            displayName = "Michael Brown",
            initials = "MB",
            sessionCount = 1,
            lastSession = "May 18",
            tags =
                listOf(
                    "Graduation",
                ),
        )

    val clients =
        listOf(
            johnSmith,
            sarahJohnson,
            emilyDavis,
            michaelBrown,
        )

    val successState =
        ClientsUiState(
            clients = UiState.Success(clients),
        )

    val loadingState =
        ClientsUiState(
            clients = UiState.Loading,
        )

    val emptyState =
        ClientsUiState(
            clients = UiState.Empty,
        )

    val errorState =
        ClientsUiState(
            clients =
                UiState.Error(
                    message = "Clients could not be loaded.",
                ),
        )
}
