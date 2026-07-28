package com.yellowtrack.platform.feature.dashboard.presentation

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.dashboard.presentation.sample.DashboardSampleData

internal class DashboardViewModel {
    val uiState =
        DashboardUiState(
            summary =
                UiState.Success(
                    DashboardSampleData.summary,
                ),
        )

    fun retry() {
        // Real reloading will be added when Dashboard has a repository.
    }
}
