package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * The badge previously read "Genesis" — the codename of the 0.1.0 milestone, left over
 * from the original scaffold and meaningless to anyone using the application. It now
 * carries today's date, which is the one piece of context a shoot-day tool should always
 * be showing.
 */
@Composable
internal fun DashboardHeader(
    today: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
    ) {
        YTBadge(text = today)

        Text(
            text = "Dashboard",
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        Text(
            text = "Enquiries waiting on you, today's schedule, and recent clients.",
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}
