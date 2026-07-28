package com.yellowtrack.platform.feature.clients.presentation.list

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.list.model.ClientSummary

internal data class ClientsUiState(
    val clients: UiState<List<ClientSummary>>,
    val query: String = "",
    /** Distinguishes "no clients yet" from "no clients match this search". */
    val isSearching: Boolean = false,
)
