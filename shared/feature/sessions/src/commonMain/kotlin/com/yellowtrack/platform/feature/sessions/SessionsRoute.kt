package com.yellowtrack.platform.feature.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.feature.sessions.presentation.SessionsScreen
import com.yellowtrack.platform.feature.sessions.presentation.SessionsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SessionsRoute(
    onSessionSelected: (SessionId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: SessionsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SessionsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onSessionSelected = onSessionSelected,
        modifier = modifier,
    )
}
