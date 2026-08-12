package com.yellowtrack.platform.feature.display.presentation

import androidx.compose.foundation.Image
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTCard
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTLoadingIndicator
import com.yellowtrack.platform.core.designsystem.component.YTQrCode
import com.yellowtrack.platform.core.designsystem.component.YTTextButton
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.resources.Res
import com.yellowtrack.platform.core.designsystem.resources.yellow_track_mark
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.ui.state.UiState
import org.jetbrains.compose.resources.painterResource

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
    onOpenWalkUp: () -> Unit,
    onCloseWalkUp: () -> Unit,
    onTypeWalkUp: (String?, String?, String?, String?) -> Unit,
    onSubmitWalkUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Always dark, whatever the tablet is set to.
    //
    // Not a preference. The code's field is the brand's yellow, and on a light background
    // that field has barely any contrast with what surrounds it — about 1.7 to 1 — so the
    // reader cannot find the code's edges and refuses it outright. Verified: the same screen
    // decodes under the dark scheme and fails under the light one, and the device this ran on
    // first was set to light.
    //
    // It is also simply what a sign should do. This is furniture in a venue, not a screen
    // somebody chose a theme for, and it should look the same on every tablet a studio owns.
    YellowTrackTheme(darkTheme = true) {
        Surface(modifier = modifier.fillMaxSize(), color = YTTheme.colors.background) {
            DisplayContents(
                uiState = uiState,
                onShow = onShow,
                onRetry = onRetry,
                onAskToLeave = onAskToLeave,
                onCancelLeaving = onCancelLeaving,
                onTypePassword = onTypePassword,
                onConfirmUnlock = onConfirmUnlock,
                onDismissProblem = onDismissProblem,
                onOpenWalkUp = onOpenWalkUp,
                onCloseWalkUp = onCloseWalkUp,
                onTypeWalkUp = onTypeWalkUp,
                onSubmitWalkUp = onSubmitWalkUp,
            )
        }
    }
}

@Composable
private fun DisplayContents(
    uiState: DisplayUiState,
    onShow: (String) -> Unit,
    onRetry: () -> Unit,
    onAskToLeave: () -> Unit,
    onCancelLeaving: () -> Unit,
    onTypePassword: (String) -> Unit,
    onConfirmUnlock: () -> Unit,
    onDismissProblem: () -> Unit,
    onOpenWalkUp: () -> Unit,
    onCloseWalkUp: () -> Unit,
    onTypeWalkUp: (String?, String?, String?, String?) -> Unit,
    onSubmitWalkUp: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
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
                        onOpenWalkUp = onOpenWalkUp,
                        onCloseWalkUp = onCloseWalkUp,
                        onTypeWalkUp = onTypeWalkUp,
                        onSubmitWalkUp = onSubmitWalkUp,
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
    onOpenWalkUp: () -> Unit,
    onCloseWalkUp: () -> Unit,
    onTypeWalkUp: (String?, String?, String?, String?) -> Unit,
    onSubmitWalkUp: () -> Unit,
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
                color = YTTheme.colors.primary,
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
                    // The brand's own colours, within the one rule a code imposes: the
                    // modules must be the darker of the two. Yellow is the field, not the
                    // modules — a pale module colour on a dark field is an inverted code, and
                    // a good many readers refuse those outright.
                    dark = CODE_DARK,
                    light = CODE_LIGHT,
                    logo = painterResource(Res.drawable.yellow_track_mark),
                    // The mark is yellow, so it needs the dark plate the launcher icon gives
                    // it. On the yellow field it would be a yellow square.
                    logoPlate = CODE_DARK,
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

        // The opposite corner, and this one is for the guest rather than the studio. Somebody
        // who left their phone on a table, or whose battery is dead, is otherwise turned away
        // by a screen whose only instruction is to point a camera at it.
        if (!showing.withdrawn) {
            YTTextButton(
                text = "No phone? Register here",
                onClick = onOpenWalkUp,
                modifier = Modifier.align(Alignment.BottomStart).padding(YTTheme.spacing.small),
            )
        }
    }

    showing.walkUp?.let { form ->
        WalkUpForm(
            form = form,
            eventName = showing.event.name,
            onClose = onCloseWalkUp,
            onType = onTypeWalkUp,
            onSubmit = onSubmitWalkUp,
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
 * The sign-up page's questions, on the device instead of on a phone.
 *
 * Deliberately the same fields in the same order, so a studio that has seen one has seen the
 * other and a guest comparing with the person beside them sees the same form.
 *
 * It clears itself when it is done and when it is abandoned. This dialog stands open on a
 * table in a room full of strangers with somebody's address half typed into it, and the next
 * person to walk up must not find it there.
 */
@Composable
private fun WalkUpForm(
    form: WalkUp,
    eventName: String,
    onClose: () -> Unit,
    onType: (String?, String?, String?, String?) -> Unit,
    onSubmit: () -> Unit,
) {
    if (form.done) {
        // One button, not the form dialog's two. "Close" beside "Done" is two words for the
        // same act, and this is read by somebody who has just handed over their details and
        // wants to know they are finished.
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("You are signed up", style = YTTheme.typography.headlineSmall) },
            text = {
                Text(
                    "Your photographs will be emailed to you when the photographer has " +
                        "finished with them. Check that email for your number.",
                    style = YTTheme.typography.bodyLarge,
                )
            },
            confirmButton = { YTTextButton(text = "Done", onClick = onClose) },
        )

        return
    }

    // An AlertDialog rather than the form dialog, so the masthead can be the page's masthead:
    // the mark, the name, the heading and the event, all centred. Somebody at a table may well
    // see this and a printed card side by side, and they should be the same invitation.
    AlertDialog(
        onDismissRequest = onClose,
        title = { WalkUpMasthead(eventName) },
        confirmButton = {
            YTTextButton(
                text = if (form.isSubmitting) "Sending…" else "Register me",
                onClick = onSubmit,
                enabled = form.canSubmit,
            )
        },
        dismissButton = { YTTextButton(text = "Cancel", onClick = onClose) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
                form.problem?.let { problem ->
                    Text(problem, style = YTTheme.typography.bodyMedium, color = YTTheme.colors.error)
                }

                YTTextField(
                    value = form.email,
                    onValueChange = { onType(it, null, null, null) },
                    label = "Email address",
                    keyboardType = KeyboardType.Email,
                    enabled = !form.isSubmitting,
                )
                YTTextField(
                    value = form.givenName,
                    onValueChange = { onType(null, it, null, null) },
                    label = "First name",
                    enabled = !form.isSubmitting,
                )
                YTTextField(
                    value = form.familyName,
                    onValueChange = { onType(null, null, it, null) },
                    label = "Last name",
                    enabled = !form.isSubmitting,
                )
                YTTextField(
                    value = form.phone,
                    onValueChange = { onType(null, null, null, it) },
                    label = "Mobile number (optional)",
                    keyboardType = KeyboardType.Phone,
                    enabled = !form.isSubmitting,
                )

                Text(
                    "The photographer will email your photographs when they have finished " +
                        "with them. Your name tells them which pictures are yours.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * The sign-up page's masthead, on the device.
 *
 * Who is asking, before anything is asked for — the same reason the page carries it. Somebody
 * about to type their name, address and telephone number into a tablet on a table deserves
 * the same answer they would get on their own phone.
 */
@Composable
private fun WalkUpMasthead(eventName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        Image(
            painter = painterResource(Res.drawable.yellow_track_mark),
            contentDescription = null,
            modifier = Modifier.height(MASTHEAD_MARK_HEIGHT),
        )
        Text("Yellow Track", style = YTTheme.typography.titleMedium)
        Text(
            "Get your photographs",
            style = YTTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            eventName,
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Big enough to scan from across a table, with room left for the name above and the link
 * below. Measured against the shorter side so rotating the device does not change it.
 */
private const val CODE_FRACTION_OF_SHORTER_SIDE = 0.62f

/**
 * The code's own two colours.
 *
 * Not taken from the theme. The theme follows the device between light and dark, and a code
 * that changed colour with the tablet's mood would be a code whose contrast nobody had
 * checked in one of the two states. These are fixed, measured, and the same on every device:
 * about eleven to one, which is far more than a camera needs.
 */
private val CODE_DARK = Color(0xFF111111)
private val CODE_LIGHT = Color(0xFFFAB91D)

/** Small enough to be a masthead rather than a picture somebody has to scroll past. */
private val MASTHEAD_MARK_HEIGHT = 40.dp
