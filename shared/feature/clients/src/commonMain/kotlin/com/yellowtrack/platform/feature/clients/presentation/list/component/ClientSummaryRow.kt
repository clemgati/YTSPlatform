package com.yellowtrack.platform.feature.clients.presentation.list.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTIcon
import com.yellowtrack.platform.core.designsystem.component.YTIcons
import com.yellowtrack.platform.core.designsystem.component.YTListItem
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.feature.clients.presentation.list.model.ClientSummary

@Composable
internal fun ClientSummaryRow(
    client: ClientSummary,
    onClick: (ClientId) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTListItem(
        modifier = modifier,
        leadingContent = {
            YTBadge(
                text = client.initials,
            )
        },
        titleContent = {
            Text(
                text = client.displayName,
                style = YTTheme.typography.titleMedium,
                color = YTTheme.colors.onSurface,
            )
        },
        subtitleContent = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        YTTheme.spacing.extraSmall,
                    ),
            ) {
                Text(
                    text = client.sessionDescription,
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                if (client.tags.isNotEmpty()) {
                    Text(
                        text = client.tags.joinToString(separator = " • "),
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.primary,
                    )
                }
            }
        },
        trailingContent = {
            YTIcon(
                icon = YTIcons.More,
                contentDescription = "More options for ${client.displayName}",
            )
        },
        onClick = {
            onClick(client.id)
        },
    )
}

private val ClientSummary.sessionDescription: String
    get() =
        buildString {
            append(sessionCount)
            append(if (sessionCount == 1) " session" else " sessions")

            lastSession?.let { date ->
                append(" • Last session ")
                append(date)
            }
        }
