package com.yellowtrack.platform.feature.clients.presentation.details

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientDetailsModel

internal data class ClientDetailsUiState(
    val client: UiState<ClientDetailsModel>,
)
