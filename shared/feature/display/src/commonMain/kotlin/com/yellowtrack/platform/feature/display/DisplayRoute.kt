package com.yellowtrack.platform.feature.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.feature.display.presentation.DisplayScreen
import com.yellowtrack.platform.feature.display.presentation.DisplayViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DisplayRoute(modifier: Modifier = Modifier) {
    val viewModel: DisplayViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    /*
     * The device follows the server for as long as it is switched on.
     *
     * This is the only screen in the project where nobody is watching — it is furniture, and
     * the studio is across the room. So it has to notice two things on its own: a code
     * withdrawn from the laptop, which must come off the table, and a new event opened, which
     * has to be in the list when somebody eventually unlocks it.
     *
     * In the composition rather than the view model for the same reason as the Events screen:
     * it should run while the screen exists and stop when it does not.
     */
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_INTERVAL_MILLIS)
            viewModel.poll()
        }
    }

    DisplayScreen(
        uiState = uiState,
        onShow = viewModel::show,
        onRetry = viewModel::refresh,
        onAskToLeave = viewModel::askToLeave,
        onCancelLeaving = viewModel::cancelLeaving,
        onTypePassword = viewModel::typePassword,
        onConfirmUnlock = viewModel::confirmUnlock,
        onDismissProblem = viewModel::dismissProblem,
        modifier = modifier,
    )
}

/**
 * Slower than the studio's own screen, and deliberately.
 *
 * Nothing here is being waited on by somebody holding a camera — the only changes that matter
 * are a studio withdrawing a code and a studio opening one, both of which are decisions taken
 * at a laptop rather than events in a queue. Five seconds is quick enough that a withdrawn
 * code leaves the table before anybody could scan it, and slow enough that a device sat on a
 * venue's wifi for ten hours is not making a request every two seconds all day.
 */
private const val REFRESH_INTERVAL_MILLIS = 5_000L
