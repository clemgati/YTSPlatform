package com.yellowtrack.platform.feature.clients.presentation.list.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsScreen

@Preview
@Composable
private fun ClientsScreenPreview() {
    YellowTrackTheme {
        ClientsScreen(
            uiState = ClientsPreviewData.successState,
            onRetry = {},
            onQueryChange = {},
            onClientSelected = {},
        )
    }
}

@Preview
@Composable
private fun ClientsScreenLoadingPreview() {
    YellowTrackTheme {
        ClientsScreen(
            uiState = ClientsPreviewData.loadingState,
            onRetry = {},
            onQueryChange = {},
            onClientSelected = {},
        )
    }
}

@Preview
@Composable
private fun ClientsScreenEmptyPreview() {
    YellowTrackTheme {
        ClientsScreen(
            uiState = ClientsPreviewData.emptyState,
            onRetry = {},
            onQueryChange = {},
            onClientSelected = {},
        )
    }
}

@Preview
@Composable
private fun ClientsScreenErrorPreview() {
    YellowTrackTheme {
        ClientsScreen(
            uiState = ClientsPreviewData.errorState,
            onRetry = {},
            onQueryChange = {},
            onClientSelected = {},
        )
    }
}
