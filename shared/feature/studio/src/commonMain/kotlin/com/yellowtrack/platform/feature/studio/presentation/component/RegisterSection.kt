package com.yellowtrack.platform.feature.studio.presentation.component

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
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
import com.yellowtrack.platform.feature.studio.presentation.model.VolumeItem
import com.yellowtrack.platform.feature.studio.presentation.model.VolumeRegister

/**
 * Where the studio's files actually live.
 *
 * The list is not the point. The point is the line at the top: when a drive dies, a studio
 * needs to know within seconds how many shoots were on it, and until there was a register
 * the only way to find out was to open every booking one at a time.
 */
@Composable
internal fun RegisterSection(
    register: VolumeRegister,
    onAddVolume: () -> Unit,
    onMarkChecked: (StorageVolumeId) -> Unit,
    onSetStatus: (StorageVolumeId, VolumeStatus) -> Unit,
    onDeleteVolume: (StorageVolumeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Drives",
        modifier = modifier,
        actions = {
            TextButton(onClick = onAddVolume) {
                Text(
                    text = "Add a drive",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        },
    ) {
        if (register.volumes.isEmpty()) {
            Text(
                text =
                    "No drives listed. Recording them is what lets the studio answer the only " +
                        "question that matters the day one fails: what was on it?",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
            return@YTSectionCard
        }

        register.warnings().forEach { warning ->
            Text(
                text = warning,
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.error,
            )
        }

        register.volumes.forEach { volume ->
            VolumeRow(volume, onMarkChecked, onSetStatus, onDeleteVolume)
        }
    }
}

/** What is wrong with the register, worst first. */
private fun VolumeRegister.warnings(): List<String> =
    buildList {
        if (failedCount > 0) {
            add(
                "$failedCount ${if (failedCount == 1) "drive has" else "drives have"} failed, " +
                    "holding $copiesAtRisk ${if (copiesAtRisk == 1) "copy" else "copies"}",
            )
        }

        // A drive nobody has ever opened is the one that fails silently and is found out
        // on the day it is needed.
        if (neverCheckedCount > 0) {
            add("$neverCheckedCount never checked")
        }
    }

@Composable
private fun VolumeRow(
    volume: VolumeItem,
    onMarkChecked: (StorageVolumeId) -> Unit,
    onSetStatus: (StorageVolumeId, VolumeStatus) -> Unit,
    onDelete: (StorageVolumeId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
        ) {
            Text(
                text = volume.label,
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )

            Text(
                text =
                    listOfNotNull(
                        volume.kindLabel,
                        volume.whereLabel,
                        volume.checkedLabel,
                        volume.shootsLabel(),
                    ).joinToString(" · "),
                style = YTTheme.typography.bodySmall,
                color = if (volume.isDependable) YTTheme.colors.onSurfaceVariant else YTTheme.colors.error,
            )

            volume.notes?.let { note ->
                Text(
                    text = note,
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        }

        if (!volume.isDependable) YTBadge(text = volume.statusLabel)

        if (volume.isDependable) {
            TextButton(onClick = { onMarkChecked(volume.id) }) {
                Text(
                    text = "Checked",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.primary,
                )
            }

            // The action the register exists for. Every shoot with a copy here reports one
            // fewer copy the moment it is pressed.
            TextButton(onClick = { onSetStatus(volume.id, VolumeStatus.Failed) }) {
                Text(
                    text = "Failed",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.error,
                )
            }
        } else {
            TextButton(onClick = { onSetStatus(volume.id, VolumeStatus.InUse) }) {
                Text(
                    text = "Back in use",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.primary,
                )
            }

            TextButton(onClick = { onDelete(volume.id) }) {
                Text(
                    text = "Remove",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Said only once there is something on it — "0 shoots" on a new drive is noise. */
private fun VolumeItem.shootsLabel(): String? =
    when (copyCount) {
        0 -> null
        1 -> "1 shoot"
        else -> "$copyCount shoots"
    }
