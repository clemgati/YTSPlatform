package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardClient

@Composable
internal fun DashboardRecentClientsSection(
    clients: List<DashboardClient>,
    modifier: Modifier = Modifier,
) {
    YTCard(
        modifier = modifier,
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.medium,
                ),
        ) {
            Text(
                text = "Recent Clients",
                style = YTTheme.typography.titleLarge,
            )

            if (clients.isEmpty()) {
                Text(
                    text = "No recent clients yet.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                clients.forEachIndexed { index, client ->
                    Text(
                        text = client.name,
                        style = YTTheme.typography.bodyLarge,
                        color = YTTheme.colors.onSurface,
                    )

                    if (index < clients.lastIndex) {
                        HorizontalDivider(
                            color = YTTheme.colors.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}
