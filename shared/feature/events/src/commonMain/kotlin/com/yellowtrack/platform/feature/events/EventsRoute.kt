package com.yellowtrack.platform.feature.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.feature.events.presentation.EventsScreen
import com.yellowtrack.platform.feature.events.presentation.EventsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EventsRoute(modifier: Modifier = Modifier) {
    val viewModel: EventsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    EventsScreen(
        uiState = uiState,
        onRetry = viewModel::refresh,
        onOpenEvent = viewModel::open,
        onCloseEvent = viewModel::closeEvent,
        onCreateEvent = { viewModel.createEvent(it) },
        onOpenStation = viewModel::openStation,
        onCloseStation = viewModel::closeStation,
        onWatchFolder = viewModel::watchFolder,
        onStopWatching = viewModel::stopWatching,
        onDismissProblem = viewModel::dismissProblem,
        modifier = modifier,
    )
}
