package com.yellowtrack.platform.feature.auth.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.component.YTTextButton
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import org.jetbrains.compose.resources.painterResource
import yellow_track_platform.shared.feature.auth.generated.resources.Res
import yellow_track_platform.shared.feature.auth.generated.resources.yellow_track_mark

/**
 * The way in.
 *
 * One form for both signing in and starting a studio, because from the studio's side it is
 * one decision — get me to my data — and two screens make somebody who guessed wrong start
 * over.
 *
 * Nothing here is reachable without it: every screen behind this one reads studio-scoped
 * data, and until 0.7.0 the studio was a constant nobody had to prove they owned.
 */
@Composable
internal fun SignInScreen(
    uiState: SignInUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
    onStudioNameChanged: (String) -> Unit,
    onCodeChanged: (String) -> Unit,
    onModeChanged: (SignInMode) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val signingUp = uiState.mode == SignInMode.SignUp
    val asking = uiState.mode == SignInMode.ForgotPassword
    val entering = uiState.mode == SignInMode.EnterCode
    val resetting = asking || entering

    // Centred rather than pinned to the top. On a phone the form is about half the height
    // of the screen, so anchored at the top it sat under the status bar with a third of the
    // display empty beneath it.
    //
    // The Box is what makes both true at once: it bounds the column to the window, so the
    // column centres while it fits and scrolls once it does not — which is what happens the
    // moment a keyboard opens over it.
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(YTTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                // Capped rather than filling: a form stretched across a 27-inch display is a
                // line of text nobody can follow back to its label.
                modifier = Modifier.widthIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
                // The mark and the name are a masthead and read as one thing. The card
                // below them fills the width, so it is unaffected.
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The mark, then the name. This is the only screen a studio meets before
                // signing in, so it is the one place worth saying whose application this is
                // in something other than words.
                //
                // The mark alone, without the black plate the launcher icon needs: on a
                // background this dark the plate reads as a square drawn round the logo.
                Image(
                    painter = painterResource(Res.drawable.yellow_track_mark),
                    contentDescription = null,
                    modifier = Modifier.height(56.dp),
                )

                Text(
                    text = "Yellow Track",
                    // A size down from the headline: beneath the mark it is a caption to
                    // it rather than the loudest thing on the screen.
                    style = YTTheme.typography.headlineMedium,
                    color = YTTheme.colors.onBackground,
                )

                YTSectionCard(
                    title =
                        when (uiState.mode) {
                            SignInMode.SignUp -> "Start your studio"
                            SignInMode.ForgotPassword -> "Reset your password"
                            SignInMode.EnterCode -> "Enter your code"
                            SignInMode.SignIn -> "Sign in"
                        },
                ) {
                    Text(
                        text =
                            when (uiState.mode) {
                                SignInMode.SignUp -> "Your bookings, ledger and shoot days, on every device you use."
                                SignInMode.ForgotPassword ->
                                    "We will email you a code. Resetting signs you out everywhere, " +
                                        "including this device."
                                SignInMode.EnterCode -> "Check your email, then set a new password."
                                SignInMode.SignIn -> "Welcome back."
                            },
                        style = YTTheme.typography.bodyMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )

                    uiState.notice?.let { notice ->
                        Text(
                            text = notice,
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.primary,
                        )
                    }

                    YTTextField(
                        value = uiState.fields.email,
                        onValueChange = onEmailChanged,
                        label = "Email",
                        keyboardType = KeyboardType.Email,
                    )

                    if (entering) {
                        YTTextField(
                            value = uiState.fields.code,
                            onValueChange = onCodeChanged,
                            label = "Code from the email",
                            placeholder = "XXXXX-XXXXX",
                            help = "It works once and expires an hour after it was sent.",
                        )
                    }

                    if (!asking) {
                        YTTextField(
                            value = uiState.fields.password,
                            onValueChange = onPasswordChanged,
                            label = if (entering) "New password" else "Password",
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            imeAction = if (signingUp) ImeAction.Next else ImeAction.Done,
                            help = "At least 12 characters.".takeIf { signingUp || entering },
                        )
                    }

                    if (signingUp) {
                        YTTextField(
                            value = uiState.fields.name,
                            onValueChange = onNameChanged,
                            label = "Your name",
                        )

                        YTTextField(
                            value = uiState.fields.studioName,
                            onValueChange = onStudioNameChanged,
                            label = "Studio name",
                            imeAction = ImeAction.Done,
                            help = "This goes on every invoice and call sheet you send.",
                        )
                    }

                    uiState.error?.let { error ->
                        Text(
                            text = error,
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.error,
                        )
                    }

                    YTButton(
                        text =
                            when {
                                uiState.isWorking -> "Just a moment…"
                                signingUp -> "Create studio"
                                asking -> "Email me a code"
                                entering -> "Set new password"
                                else -> "Sign in"
                            },
                        onClick = onSubmit,
                        enabled = uiState.canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // The lesser action, and it must look like it. Two filled buttons leave the
                    // screen with no primary action at all.
                    YTTextButton(
                        text =
                            when {
                                resetting -> "Back to signing in"
                                signingUp -> "I already have an account"
                                else -> "Start a new studio"
                            },
                        onClick = {
                            onModeChanged(
                                when {
                                    resetting -> SignInMode.SignIn
                                    signingUp -> SignInMode.SignIn
                                    else -> SignInMode.SignUp
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Only offered where it makes sense. On the sign-up form it would be an
                    // invitation to reset a password that does not exist yet.
                    if (uiState.mode == SignInMode.SignIn) {
                        YTTextButton(
                            text = "I have forgotten my password",
                            onClick = { onModeChanged(SignInMode.ForgotPassword) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Said plainly rather than buried. On a browser the token is readable by any
                // script on the page, and a studio signing in on a shared machine should know
                // that before it does rather than after.
                if (!uiState.isHardwareBacked) {
                    Text(
                        text =
                            "This device cannot store your sign-in securely — anything with access " +
                                "to it can read your session. Sign out when you are finished.",
                        style = YTTheme.typography.bodySmall,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
