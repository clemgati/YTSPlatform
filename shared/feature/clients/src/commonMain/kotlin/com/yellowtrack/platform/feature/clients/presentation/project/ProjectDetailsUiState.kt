package com.yellowtrack.platform.feature.clients.presentation.project

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.project.model.ProjectDetailsModel

internal data class ProjectDetailsUiState(
    val project: UiState<ProjectDetailsModel>,
    val currency: CurrencyCode = CurrencyCode.USD,
)
