package com.yellowtrack.platform.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
    onModeChanged: (SignInMode) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val signingUp = uiState.mode == SignInMode.SignUp

    Column(
        modifier =
            modifier
                .fillMaxSize()
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
        ) {
            Text(
                text = "Yellow Track",
                style = YTTheme.typography.headlineLarge,
                color = YTTheme.colors.onBackground,
            )

            YTSectionCard(title = if (signingUp) "Start your studio" else "Sign in") {
                Text(
                    text =
                        if (signingUp) {
                            "Your bookings, ledger and shoot days, on every device you use."
                        } else {
                            "Welcome back."
                        },
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                YTTextField(
                    value = uiState.fields.email,
                    onValueChange = onEmailChanged,
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                )

                YTTextField(
                    value = uiState.fields.password,
                    onValueChange = onPasswordChanged,
                    label = "Password",
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    imeAction = if (signingUp) ImeAction.Next else ImeAction.Done,
                    help = "At least 12 characters.".takeIf { signingUp },
                )

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
                            else -> "Sign in"
                        },
                    onClick = onSubmit,
                    enabled = uiState.canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )

                // The lesser action, and it must look like it. Two filled buttons leave the
                // screen with no primary action at all.
                YTTextButton(
                    text = if (signingUp) "I already have an account" else "Start a new studio",
                    onClick = { onModeChanged(if (signingUp) SignInMode.SignIn else SignInMode.SignUp) },
                    modifier = Modifier.fillMaxWidth(),
                )
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
