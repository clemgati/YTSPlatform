package com.yellowtrack.platform.feature.sessions.presentation

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionGroup

internal data class SessionsUiState(
    val groups: UiState<List<SessionGroup>>,
    val totalCount: Int = 0,
)
