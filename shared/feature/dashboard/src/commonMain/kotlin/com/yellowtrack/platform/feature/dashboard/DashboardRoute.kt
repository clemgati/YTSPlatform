package com.yellowtrack.platform.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardScreen
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardViewModel

@Composable
fun DashboardRoute(modifier: Modifier = Modifier) {
    val viewModel =
        remember {
            DashboardViewModel()
        }

    DashboardScreen(
        uiState = viewModel.uiState,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}
