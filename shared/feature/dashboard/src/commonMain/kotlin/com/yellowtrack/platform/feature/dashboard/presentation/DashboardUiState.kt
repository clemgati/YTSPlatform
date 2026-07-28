package com.yellowtrack.platform.feature.dashboard.presentation

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSummary

internal data class DashboardUiState(
    val summary: UiState<DashboardSummary>,
)
