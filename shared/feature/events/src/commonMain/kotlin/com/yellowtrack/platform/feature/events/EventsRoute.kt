package com.yellowtrack.platform.feature.events

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.feature.events.presentation.EventsScreen
import com.yellowtrack.platform.feature.events.presentation.EventsViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun EventsRoute(modifier: Modifier = Modifier) {
    val viewModel: EventsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    /*
     * An open event follows the server while it is on screen.
     *
     * Everything was loaded once and then frozen: somebody who scanned the code while the
     * photographer had the event open never appeared in the list to seat them from, and a
     * sitting's photograph count stopped climbing while ingest was still uploading. Both look
     * like the software losing them.
     *
     * Here rather than in the view model because it should run while somebody is *looking* —
     * which is what a composition is — and stop when they are not.
     */
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_INTERVAL_MILLIS)
            viewModel.refreshOpenEvent()
        }
    }

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
        onSeat = viewModel::seat,
        onDeliver = viewModel::deliver,
        onPrintSignUpCode = viewModel::printSignUpCode,
        onWithdrawSignUpCode = viewModel::withdrawSignUpCode,
        onDismissNote = viewModel::dismissNote,
        onDismissProblem = viewModel::dismissProblem,
        modifier = modifier,
    )
}

/**
 * Often enough that a queue moves, rarely enough to be invisible.
 *
 * Matched to the ingest sweep, so a photograph count and the folder it came from change at
 * the same pace rather than disagreeing for a few seconds at a time.
 */
private const val REFRESH_INTERVAL_MILLIS = 2_000L
