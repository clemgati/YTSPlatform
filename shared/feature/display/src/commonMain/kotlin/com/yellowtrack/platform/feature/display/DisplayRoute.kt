package com.yellowtrack.platform.feature.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.display.presentation.DisplayScreen
import com.yellowtrack.platform.feature.display.presentation.DisplayViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DisplayRoute(
    modifier: Modifier = Modifier,
    /**
     * Called with true while a code is on the table, and false while it is not.
     *
     * The feature does not know what a host does with this. On Android it pins the screen,
     * so the back gesture cannot walk out of an unattended device and around the password;
     * elsewhere it does nothing. Expressed as a fact about the screen rather than as
     * "pin now", because that is the only part of it this module can be right about.
     */
    onDisplayingChanged: (Boolean) -> Unit = {},
) {
    val viewModel: DisplayViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val isDisplaying = (uiState.content as? UiState.Success)?.data?.showing != null

    LaunchedEffect(isDisplaying) { onDisplayingChanged(isDisplaying) }

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

    /*
     * The walk-up form clears itself when it is left alone.
     *
     * It stands open on a table in a room full of strangers, with somebody's address and
     * telephone number half typed into it. Somebody who wanders off mid-form must not leave
     * that there for the next person to read, or to submit under their own address.
     *
     * Keyed on the interaction count, so every keystroke and every touch starts the wait
     * again — a person reading the form slowly is not somebody who has abandoned it.
     */
    val walkUp = (uiState.content as? UiState.Success)?.data?.showing?.walkUp

    LaunchedEffect(walkUp != null, walkUp?.interactions, walkUp?.done) {
        if (walkUp == null) return@LaunchedEffect

        delay(if (walkUp.done) CONFIRMATION_MILLIS else ABANDONED_MILLIS)
        viewModel.closeWalkUp()
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
        onOpenWalkUp = viewModel::openWalkUp,
        onCloseWalkUp = viewModel::closeWalkUp,
        onTypeWalkUp = viewModel::typeWalkUp,
        onSubmitWalkUp = viewModel::submitWalkUp,
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

/**
 * How long the walk-up form waits before deciding nobody is filling it in.
 *
 * Three minutes: long enough to find an email address on a lanyard or ask somebody how to
 * spell a surname, short enough that a form abandoned at a busy table is gone before the next
 * person reaches it.
 */
private const val ABANDONED_MILLIS = 3 * 60 * 1_000L

/**
 * And how long "you are signed up" stays before the device is ready for the next person.
 *
 * Long enough to read, short enough that somebody walking up does not have to dismiss
 * a stranger's confirmation to reach the form.
 */
private const val CONFIRMATION_MILLIS = 12 * 1_000L
