package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardClient

@Composable
internal fun DashboardRecentClientsSection(
    clients: List<DashboardClient>,
    onOpenClient: (ClientId) -> Unit,
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
                    // Named rather than the row being quietly clickable: the dashboard
                    // names an account and the obvious thing to want is to open it.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = client.name,
                            modifier = Modifier.weight(1f),
                            style = YTTheme.typography.bodyLarge,
                            color = YTTheme.colors.onSurface,
                        )

                        TextButton(onClick = { onOpenClient(client.id) }) {
                            Text(
                                text = "Open",
                                style = YTTheme.typography.labelMedium,
                                color = YTTheme.colors.primary,
                            )
                        }
                    }

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
