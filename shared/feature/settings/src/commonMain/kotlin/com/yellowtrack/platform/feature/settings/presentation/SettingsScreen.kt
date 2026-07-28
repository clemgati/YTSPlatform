package com.yellowtrack.platform.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.ui.component.EmptyContent

/**
 * Placeholder until settings have something to configure.
 *
 * States that plainly rather than showing switches that are wired to nothing. The pricing
 * basis — the one setting the platform actually has — is edited on the Ledger, beside the
 * figure it changes, which is where it belongs.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(YTTheme.spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
    ) {
        Text(
            text = "Settings",
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        EmptyContent(
            title = "Nothing to configure yet",
            message =
                "Studio details, tax rates, and currency arrive with the account model. " +
                    "Your pricing basis is on the Ledger, beside the floor it sets.",
        )
    }
}
