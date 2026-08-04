package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSession

@Composable
internal fun DashboardSessionRow(
    session: DashboardSession,
    onOpenSession: (SessionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.medium,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        YTBadge(
            text = session.time,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.extraSmall,
                ),
        ) {
            Text(
                text = session.title,
                style = YTTheme.typography.titleMedium,
                color = YTTheme.colors.onSurface,
            )

            Text(
                text = session.clientName,
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        // The dashboard names today's shoot and the obvious next thing is to open it —
        // for the call sheet, the shot list, or to say a card has been copied.
        TextButton(onClick = { onOpenSession(session.id) }) {
            Text(
                text = "Open",
                style = YTTheme.typography.labelMedium,
                color = YTTheme.colors.primary,
            )
        }
    }
}
