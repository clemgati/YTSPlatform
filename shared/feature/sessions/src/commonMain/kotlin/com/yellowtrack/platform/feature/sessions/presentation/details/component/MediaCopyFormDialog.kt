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

/**
 * Records that a copy of the files exists somewhere.
 *
 * A copy is recorded as unchecked. It has just been made, and whether it can still be read
 * is a different question asked later — marking it verified on creation would make every
 * backup look checked when none had been.
 */
@Composable
internal fun MediaCopyFormDialog(
    onSave: (NewMediaCopy) -> Unit,
    onDismiss: () -> Unit,
) {
    var volumeName by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(StorageKind.ExternalDrive) }
    var isOffsite by remember { mutableStateOf(false) }

    YTFormDialog(
        title = "Record a copy",
        confirmLabel = "Add",
        confirmEnabled = volumeName.isNotBlank(),
        onConfirm = {
            onSave(
                NewMediaCopy(
                    volumeName = volumeName.trim(),
                    kind = kind,
                    isOffsite = isOffsite,
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
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

        if (!kind.isInherentlyOffsite) {
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

        if (kind == StorageKind.CameraCard) {
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
