package com.yellowtrack.platform.feature.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.feature.sessions.presentation.SessionsScreen
import com.yellowtrack.platform.feature.sessions.presentation.SessionsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SessionsRoute(modifier: Modifier = Modifier) {
    val viewModel: SessionsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SessionsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onAddSession = viewModel::addSession,
        onUpdateSession = viewModel::updateSession,
        onMoveSession = viewModel::moveSession,
        modifier = modifier,
    )
}
