package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSession

@Composable
internal fun DashboardTodaySessionsSection(
    sessions: List<DashboardSession>,
    onOpenSession: (SessionId) -> Unit,
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
                    onOpenSession = onOpenSession,
                )
            }
        }
    }
}
