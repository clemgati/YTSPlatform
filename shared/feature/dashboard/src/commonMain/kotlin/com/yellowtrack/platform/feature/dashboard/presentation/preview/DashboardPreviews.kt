package com.yellowtrack.platform.feature.dashboard.presentation.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardScreen

@Preview
@Composable
private fun DashboardSuccessPreview() {
    YellowTrackTheme {
        DashboardScreen(
            uiState = DashboardPreviewData.successState,
            onRetry = {},
            onMarkEnquiryReplied = {},
            onAddEnquiry = {},
        )
    }
}

@Preview
@Composable
private fun DashboardLoadingPreview() {
    YellowTrackTheme {
        DashboardScreen(
            uiState = DashboardPreviewData.loadingState,
            onRetry = {},
            onMarkEnquiryReplied = {},
            onAddEnquiry = {},
        )
    }
}

@Preview
@Composable
private fun DashboardEmptyPreview() {
    YellowTrackTheme {
        DashboardScreen(
            uiState = DashboardPreviewData.emptyState,
            onRetry = {},
            onMarkEnquiryReplied = {},
            onAddEnquiry = {},
        )
    }
}

@Preview
@Composable
private fun DashboardErrorPreview() {
    YellowTrackTheme {
        DashboardScreen(
            uiState = DashboardPreviewData.errorState,
            onRetry = {},
            onMarkEnquiryReplied = {},
            onAddEnquiry = {},
        )
    }
}
