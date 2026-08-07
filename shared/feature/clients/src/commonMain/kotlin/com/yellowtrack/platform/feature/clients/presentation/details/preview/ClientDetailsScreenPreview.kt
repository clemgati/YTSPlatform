package com.yellowtrack.platform.feature.clients.presentation.details.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsScreen

@Preview
@Composable
private fun ClientDetailsScreenPreview() {
    YellowTrackTheme {
        ClientDetailsScreen(
            uiState = ClientDetailsPreviewData.successState,
            onRetry = {},
            onBack = {},
            onUpdateClient = {},
            onAddProject = {},
            onOpenBooking = {},
            onScheduleSession = {},
            onRemoveClient = {},
            writeFailure = null,
        )
    }
}

@Preview
@Composable
private fun ClientDetailsScreenLoadingPreview() {
    YellowTrackTheme {
        ClientDetailsScreen(
            uiState = ClientDetailsPreviewData.loadingState,
            onRetry = {},
            onBack = {},
            onUpdateClient = {},
            onAddProject = {},
            onOpenBooking = {},
            onScheduleSession = {},
            onRemoveClient = {},
            writeFailure = null,
        )
    }
}

@Preview
@Composable
private fun ClientDetailsScreenEmptyPreview() {
    YellowTrackTheme {
        ClientDetailsScreen(
            uiState = ClientDetailsPreviewData.emptyState,
            onRetry = {},
            onBack = {},
            onUpdateClient = {},
            onAddProject = {},
            onOpenBooking = {},
            onScheduleSession = {},
            onRemoveClient = {},
            writeFailure = null,
        )
    }
}

@Preview
@Composable
private fun ClientDetailsScreenErrorPreview() {
    YellowTrackTheme {
        ClientDetailsScreen(
            uiState = ClientDetailsPreviewData.errorState,
            onRetry = {},
            onBack = {},
            onUpdateClient = {},
            onAddProject = {},
            onOpenBooking = {},
            onScheduleSession = {},
            onRemoveClient = {},
            writeFailure = null,
        )
    }
}
