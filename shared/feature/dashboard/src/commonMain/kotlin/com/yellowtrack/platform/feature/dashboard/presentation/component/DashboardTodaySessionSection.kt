package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSession

@Composable
internal fun DashboardTodaySessionsSection(
    sessions: List<DashboardSession>,
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Today's Sessions",
        modifier = modifier,
    ) {
        if (sessions.isEmpty()) {
            Text(
                text = "No sessions scheduled for today.",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        } else {
            sessions.forEach { session ->
                DashboardSessionRow(
                    session = session,
                )
            }
        }
    }
}
