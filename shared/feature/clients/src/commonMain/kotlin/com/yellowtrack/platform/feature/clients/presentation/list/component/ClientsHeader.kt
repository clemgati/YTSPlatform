package com.yellowtrack.platform.feature.clients.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
internal fun ClientsHeader(
    clientCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.small,
            ),
    ) {
        YTBadge(
            text = clientCount.clientCountLabel,
        )

        Text(
            text = "Clients",
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        Text(
            text = "Manage client profiles, sessions, contact information, and notes.",
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}

private val Int.clientCountLabel: String
    get() =
        when (this) {
            0 -> "No clients"
            1 -> "1 client"
            else -> "$this clients"
        }
