package com.yellowtrack.platform.feature.sessions.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.feature.sessions.presentation.model.NewMediaCopy
import com.yellowtrack.platform.feature.sessions.presentation.model.VolumeOption

/**
 * Records that a copy of the files exists somewhere.
 *
 * A copy is recorded as unchecked. It has just been made, and whether it can still be read
 * is a different question asked later — marking it verified on creation would make every
 * backup look checked when none had been.
 */
@Composable
internal fun MediaCopyFormDialog(
    volumes: List<VolumeOption>,
    canReadDrives: Boolean,
    onSave: (NewMediaCopy) -> Unit,
    onDismiss: () -> Unit,
) {
    // "Somewhere else" last, so a studio with a register picks from it by default and one
    // without is not blocked from recording a copy at all.
    val elsewhere =
        VolumeOption(id = null, label = "Somewhere else", kind = StorageKind.ExternalDrive, isOffsite = false)
    val options = remember(volumes) { volumes + elsewhere }
    var selected by remember(volumes) { mutableStateOf(options.first()) }

    var volumeName by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(StorageKind.ExternalDrive) }
    var isOffsite by remember { mutableStateOf(false) }
    var path by remember { mutableStateOf("") }

    val registered = selected.id != null

    YTFormDialog(
        title = "Record a copy",
        confirmLabel = "Add",
        confirmEnabled = registered || volumeName.isNotBlank(),
        onConfirm = {
            onSave(
                if (registered) {
                    // Name, kind and location come from the register rather than being
                    // retyped: a drive described differently on two shoots is two drives
                    // as far as any later question is concerned.
                    NewMediaCopy(
                        volumeId = selected.id,
                        volumeName = selected.label,
                        kind = selected.kind,
                        isOffsite = selected.isOffsite,
                        path = path.trim().ifBlank { null },
                    )
                } else {
                    NewMediaCopy(
                        volumeName = volumeName.trim(),
                        kind = kind,
                        isOffsite = isOffsite,
                        path = path.trim().ifBlank { null },
                    )
                },
            )
        },
        onDismiss = onDismiss,
    ) {
        if (volumes.isNotEmpty()) {
            YTDropdownField(
                label = "What is it on?",
                selected = selected,
                options = options,
                optionLabel = VolumeOption::label,
                onSelect = { selected = it },
                help =
                    if (registered) {
                        "From your register, so this shoot appears the day that drive fails."
                    } else {
                        "Not in your register — this copy will not be found by a drive going down."
                    },
            )
        }

        if (!registered) {
            YTTextField(
                value = volumeName,
                onValueChange = { volumeName = it },
                label = "What is it on?",
                placeholder = "Red Samsung T7",
                imeAction = ImeAction.Done,
                help = "Whatever you call it when you go looking for it.",
            )

            YTDropdownField(
                label = "Kind",
                selected = kind,
                options = StorageKind.entries,
                optionLabel = { it.label },
                onSelect = { kind = it },
                optionDescription = { it.explanation },
            )
        }

        if (!registered && !kind.isInherentlyOffsite) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
            ) {
                Checkbox(checked = isOffsite, onCheckedChange = { isOffsite = it })
                Text(
                    text = "Kept away from the studio",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurface,
                )
            }
        }

        // Only offered where this device can act on it. On the web the field would
        // collect a path nothing could ever read.
        if (canReadDrives) {
            YTTextField(
                value = path,
                onValueChange = { path = it },
                label = "Where on it?",
                placeholder = "/Volumes/Red T7/2026/Johnson Wedding",
                help =
                    "Given a folder, this can open the drive and count the files rather than " +
                        "taking your word for it. Leave blank for a cloud copy.",
            )
        }

        if (!registered && kind == StorageKind.CameraCard) {
            Text(
                text = "A card is the original, not a copy of it. It does not count towards the three.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.error,
            )
        }
    }
}

private val StorageKind.label: String
    get() =
        when (this) {
            StorageKind.CameraCard -> "Camera card"
            StorageKind.Computer -> "Computer"
            StorageKind.ExternalDrive -> "External drive"
            StorageKind.Nas -> "NAS"
            StorageKind.Cloud -> "Cloud"
            StorageKind.OffsiteDrive -> "Offsite drive"
        }

private val StorageKind.explanation: String
    get() =
        when (this) {
            StorageKind.CameraCard -> "Still in the bag. Not a backup."
            StorageKind.Computer -> "The machine you edit on."
            StorageKind.ExternalDrive -> "A drive on the desk."
            StorageKind.Nas -> "Network storage, still on the premises."
            StorageKind.Cloud -> "Off the premises by definition."
            StorageKind.OffsiteDrive -> "A drive kept somewhere else."
        }
