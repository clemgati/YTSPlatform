package com.yellowtrack.platform.feature.display.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.min
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTCard
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTLoadingIndicator
import com.yellowtrack.platform.core.designsystem.component.YTQrCode
import com.yellowtrack.platform.core.designsystem.component.YTTextButton
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.ui.state.UiState

/**
 * Two screens behind one entry point: choosing an event, and being the code.
 *
 * They are here together because the second is a mode of the first rather than a destination.
 * There is no back stack on a device propped against a water jug, and the only way out of the
 * code is the password.
 */
@Composable
internal fun DisplayScreen(
    uiState: DisplayUiState,
    onShow: (String) -> Unit,
    onRetry: () -> Unit,
    onAskToLeave: () -> Unit,
    onCancelLeaving: () -> Unit,
    onTypePassword: (String) -> Unit,
    onConfirmUnlock: () -> Unit,
    onDismissProblem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val content = uiState.content) {
            UiState.Loading -> YTLoadingIndicator()

            UiState.Empty ->
                Guidance(
                    heading = "No sign-up codes are open",
                    // The remedy, named, on the device that cannot perform it. A screen that
                    // says only "nothing here" leaves somebody standing at a table wondering
                    // whether the application is broken.
                    detail =
                        "Open an event's sign-up code in the YellowTrack app, and it will " +
                            "appear here within a few seconds.",
                    onRetry = onRetry,
                )

            is UiState.Error ->
                Guidance(
                    heading = "Cannot reach the server",
                    detail = content.message,
                    onRetry = onRetry,
                )

            is UiState.Success ->
                content.data.showing?.let { showing ->
                    ShowingCode(
                        showing = showing,
                        onAskToLeave = onAskToLeave,
                        onCancelLeaving = onCancelLeaving,
                        onTypePassword = onTypePassword,
                        onConfirmUnlock = onConfirmUnlock,
                    )
                } ?: ChoosingEvent(content = content.data, onShow = onShow)
        }

        uiState.problem?.let { problem ->
            // Along the bottom, and dismissible: it must not cover the code, which is the one
            // thing on this screen anybody came for.
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(YTTheme.spacing.medium)) {
                YTCard {
                    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
                        Text(problem, style = YTTheme.typography.bodyMedium)
                        YTTextButton(text = "Dismiss", onClick = onDismissProblem)
                    }
                }
            }
        }
    }
}

@Composable
private fun Guidance(
    heading: String,
    detail: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(YTTheme.spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(heading, style = YTTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            detail,
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        YTButton(text = "Try again", onClick = onRetry)
    }
}

@Composable
private fun ChoosingEvent(
    content: DisplayContent,
    onShow: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(YTTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
            Text(
                content.studioName,
                style = YTTheme.typography.titleMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
            Text("Choose an event to display", style = YTTheme.typography.headlineMedium)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            items(content.events, key = { it.id }) { event ->
                YTCard(modifier = Modifier.fillMaxWidth().clickable { onShow(event.id) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                        Text(event.name, style = YTTheme.typography.titleLarge)
                        Text(
                            "Sign-up is open",
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The device doing its job: an event's name, its code, and the link under it.
 *
 * Deliberately the same content as the printed card. Somebody arriving at a table where both
 * are present should not have to work out that they are the same invitation.
 */
@Composable
private fun ShowingCode(
    showing: Showing,
    onAskToLeave: () -> Unit,
    onCancelLeaving: () -> Unit,
    onTypePassword: (String) -> Unit,
    onConfirmUnlock: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // The code takes what the shorter side allows, so a device lying on its side shows the
        // same code as one stood up. It is the thing being photographed; the rest fits around
        // it.
        val side = min(maxWidth, maxHeight) * CODE_FRACTION_OF_SHORTER_SIDE

        Column(
            modifier = Modifier.fillMaxSize().padding(YTTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                showing.event.name,
                style = YTTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )

            if (showing.withdrawn) {
                Text(
                    "Sign-up has closed",
                    style = YTTheme.typography.headlineMedium,
                    color = YTTheme.colors.error,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "The code for this event has been withdrawn, so it is no longer shown.",
                    style = YTTheme.typography.bodyLarge,
                    color = YTTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    "Scan this to get your photographs",
                    style = YTTheme.typography.titleLarge,
                    color = YTTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                YTQrCode(
                    rows = showing.code.rows,
                    modifier = Modifier.width(side).height(side),
                    contentDescription = "Sign-up code for ${showing.event.name}",
                )
                Text(
                    showing.link,
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "We will email your photographs when the photographer has finished with them.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Small, and in a corner. The way off this screen should be findable by the studio and
        // uninteresting to everybody else — this is a table in a room full of strangers, and a
        // prominent "Change event" invites exactly the tap that must not happen.
        YTTextButton(
            text = "Change event",
            onClick = onAskToLeave,
            modifier = Modifier.align(Alignment.BottomEnd).padding(YTTheme.spacing.small),
        )
    }

    showing.unlock?.let { unlock ->
        YTFormDialog(
            title = "Change event",
            confirmLabel = if (unlock.isChecking) "Checking…" else "Unlock",
            onConfirm = onConfirmUnlock,
            onDismiss = onCancelLeaving,
            confirmEnabled = unlock.canSubmit,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
                Text(
                    "Enter the password for this studio's account to change which event is shown.",
                    style = YTTheme.typography.bodyMedium,
                )
                YTTextField(
                    value = unlock.password,
                    onValueChange = onTypePassword,
                    label = "Password",
                    isPassword = true,
                    errorMessage = unlock.problem,
                )
            }
        }
    }
}

/**
 * Big enough to scan from across a table, with room left for the name above and the link
 * below. Measured against the shorter side so rotating the device does not change it.
 */
private const val CODE_FRACTION_OF_SHORTER_SIDE = 0.62f
