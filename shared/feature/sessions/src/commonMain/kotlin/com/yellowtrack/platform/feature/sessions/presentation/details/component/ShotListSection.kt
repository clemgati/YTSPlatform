package com.yellowtrack.platform.feature.sessions.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.shot.ShotId
import com.yellowtrack.platform.feature.sessions.presentation.details.model.ShotGroup
import com.yellowtrack.platform.feature.sessions.presentation.details.model.ShotItem

/**
 * The photographs promised for this day, worked a group at a time.
 *
 * A finished group says so, because that is the moment a photographer can tell eleven
 * relatives they are free to go — which is the entire reason the list is grouped rather
 * than a single column of twenty lines.
 */
@Composable
internal fun ShotListSection(
    groups: List<ShotGroup>,
    remaining: Int,
    onToggleShot: (ShotId, Boolean) -> Unit,
    onDeleteShot: (ShotId) -> Unit,
    onAddShot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Shot list",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium)) {
            if (groups.isEmpty()) {
                Text(
                    text =
                        "Nothing promised yet. Family formals are worth listing before the day: " +
                            "the group nobody wrote down is the one that gets missed.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                Text(
                    text = if (remaining == 0) "Every shot taken" else "$remaining still to take",
                    style = YTTheme.typography.bodyMedium,
                    color = if (remaining == 0) YTTheme.colors.primary else YTTheme.colors.onSurfaceVariant,
                )

                groups.forEach { group ->
                    ShotGroupBlock(group, onToggleShot, onDeleteShot)
                }
            }

            TextButton(onClick = onAddShot) {
                Text(
                    text = "Add a shot",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

@Composable
private fun ShotGroupBlock(
    group: ShotGroup,
    onToggle: (ShotId, Boolean) -> Unit,
    onDelete: (ShotId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.name,
                style = YTTheme.typography.titleSmall,
                color = YTTheme.colors.onSurface,
            )

            Text(
                // The figure that decides whether this group can leave.
                text = if (group.isComplete) "done" else "${group.remaining} left",
                style = YTTheme.typography.labelMedium,
                color = if (group.isComplete) YTTheme.colors.primary else YTTheme.colors.onSurfaceVariant,
            )
        }

        group.shots.forEach { shot ->
            ShotRow(shot, onToggle, onDelete)
        }
    }
}

@Composable
private fun ShotRow(
    shot: ShotItem,
    onToggle: (ShotId, Boolean) -> Unit,
    onDelete: (ShotId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
    ) {
        Checkbox(
            checked = shot.isCaptured,
            onCheckedChange = { onToggle(shot.id, it) },
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shot.description,
                style = YTTheme.typography.bodyMedium,
                color = if (shot.isCaptured) YTTheme.colors.onSurfaceVariant else YTTheme.colors.onSurface,
                textDecoration = if (shot.isCaptured) TextDecoration.LineThrough else null,
            )

            shot.people?.let { people ->
                Text(
                    text = people,
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        }

        TextButton(onClick = { onDelete(shot.id) }) {
            Text(
                text = "Remove",
                style = YTTheme.typography.labelMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}
