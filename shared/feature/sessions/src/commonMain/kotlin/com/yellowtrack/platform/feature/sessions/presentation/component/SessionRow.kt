package com.yellowtrack.platform.feature.sessions.presentation.component

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
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionListItem

@Composable
internal fun SessionRow(
    session: SessionListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YTListItem(
        modifier = modifier,
        leadingContent = {
            YTBadge(text = session.kind.label)
        },
        titleContent = {
            Text(
                text = session.title,
                style = YTTheme.typography.titleMedium,
                color = YTTheme.colors.onSurface,
            )
        },
        subtitleContent = {
            Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                Text(
                    text = "${session.dayLabel} • ${session.timeRange}",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                Text(
                    text = session.subtitleLine,
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                session.goldenHourLabel?.let { golden ->
                    Text(
                        text = golden,
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.primary,
                    )
                }

                session.timeZoneNote?.let { zone ->
                    Text(
                        text = "Local time in $zone",
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.primary,
                    )
                }
            }
        },
        trailingContent = {
            YTIcon(
                icon = YTIcons.More,
                contentDescription = "More options for ${session.title}",
            )
        },
        onClick = onClick,
    )
}

private val SessionListItem.subtitleLine: String
    get() =
        listOfNotNull(
            clientName.takeIf(String::isNotBlank),
            locationName,
            status.name,
        ).joinToString(" • ")

private val SessionKind.label: String
    get() =
        when (this) {
            SessionKind.Consultation -> "CALL"
            SessionKind.Scout -> "SCOUT"
            SessionKind.Shoot -> "SHOOT"
            SessionKind.Pickup -> "PICKUP"
            SessionKind.Delivery -> "DELIVER"
        }
