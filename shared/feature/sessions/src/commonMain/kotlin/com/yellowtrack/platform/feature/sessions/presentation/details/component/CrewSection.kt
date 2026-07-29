package com.yellowtrack.platform.feature.sessions.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.feature.sessions.presentation.details.model.CrewItem

/**
 * Who is working the day, and when each of them is due.
 *
 * Ordered by call time rather than by role, because that is the order the morning
 * actually happens in: hair and make-up hours before the photographer, the videographer
 * after. A call sheet that lists everyone under one time is a call sheet nobody can use.
 */
@Composable
internal fun CrewSection(
    crew: List<CrewItem>,
    sessionCallTime: String?,
    onAddCrew: () -> Unit,
    onRemoveCrew: (CrewMemberId) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Crew",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            if (crew.isEmpty()) {
                Text(
                    text = "Nobody else on this day yet.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                crew.forEach { member ->
                    CrewRow(member, sessionCallTime, onRemoveCrew)
                }
            }

            TextButton(onClick = onAddCrew) {
                Text(
                    text = "Add someone",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

@Composable
private fun CrewRow(
    member: CrewItem,
    sessionCallTime: String?,
    onRemove: (CrewMemberId) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = member.name,
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )

            Text(
                // Someone with no time of their own is due when the crew is due, which is
                // said outright rather than left as a blank for them to guess at.
                text = member.callTimeLabel ?: sessionCallTime?.let { "with the crew, $it" } ?: "no call time",
                style = YTTheme.typography.titleSmall,
                color =
                    if (member.callTimeLabel != null) YTTheme.colors.primary else YTTheme.colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = listOfNotNull(member.role, member.phone).joinToString(" • "),
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )

            TextButton(onClick = { onRemove(member.id) }) {
                Text(
                    text = "Remove",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
