package com.yellowtrack.platform.feature.clients.presentation.details.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.component.YTListItem
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientSessionHistoryItem

@Composable
internal fun ClientSessionHistorySection(
    sessions: List<ClientSessionHistoryItem>,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Session History",
        modifier = modifier,
    ) {
        if (sessions.isEmpty()) {
            Text(
                text = "No previous sessions.",
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurfaceVariant,
            )
        } else {
            sessions.forEach { session ->
                YTListItem(
                    titleContent = {
                        Text(
                            text = session.title,
                            style = YTTheme.typography.titleMedium,
                            color = YTTheme.colors.onSurface,
                        )
                    },
                    subtitleContent = {
                        Text(
                            text = session.date,
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}
