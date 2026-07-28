package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
internal fun DashboardHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.small,
            ),
    ) {
        YTBadge(
            text = "Genesis",
        )

        Text(
            text = "Dashboard",
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        Text(
            text = "Your sessions, recent clients, and studio readiness at a glance.",
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}
