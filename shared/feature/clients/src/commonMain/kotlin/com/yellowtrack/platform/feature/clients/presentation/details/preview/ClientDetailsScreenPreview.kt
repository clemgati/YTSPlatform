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
            onEditClient = {},
            onArchiveClient = {},
            onScheduleSession = {},
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
            onEditClient = {},
            onArchiveClient = {},
            onScheduleSession = {},
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
            onEditClient = {},
            onArchiveClient = {},
            onScheduleSession = {},
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
            onEditClient = {},
            onArchiveClient = {},
            onScheduleSession = {},
        )
    }
}
