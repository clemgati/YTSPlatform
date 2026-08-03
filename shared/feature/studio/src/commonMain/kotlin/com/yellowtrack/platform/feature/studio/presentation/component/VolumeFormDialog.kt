package com.yellowtrack.platform.feature.studio.presentation.component

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
import com.yellowtrack.platform.core.model.media.VolumeStatus
import com.yellowtrack.platform.feature.studio.presentation.mapper.label
import com.yellowtrack.platform.feature.studio.presentation.model.NewVolume

/**
 * Adds a drive to the register.
 *
 * Where it lives is asked once, here, rather than on every copy: a drive kept at a
 * relative's house is offsite for every shoot on it, and asking a studio to remember that
 * each time is asking it to get it wrong.
 */
@Composable
internal fun VolumeFormDialog(
    onSave: (NewVolume) -> Unit,
    onDismiss: () -> Unit,
    /** The drive being corrected, or null when this is a new one. */
    initial: NewVolume? = null,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: StorageKind.ExternalDrive) }
    var isOffsite by remember { mutableStateOf(initial?.isOffsite ?: false) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }

    YTFormDialog(
        title = if (initial == null) "Add a drive" else "Edit this drive",
        confirmLabel = if (initial == null) "Save" else "Save changes",
        confirmEnabled = label.isNotBlank(),
        onConfirm = {
            onSave(
                NewVolume(
                    label = label,
                    kind = kind,
                    // Carried across rather than reset. The form has no status field —
                    // failing a drive is a separate action on the register — so building
                    // one here would quietly revive a drive somebody marked dead.
                    status = initial?.status ?: VolumeStatus.InUse,
                    // Cloud and offsite drives are away by definition, so the tick is not
                    // asked for twice.
                    isOffsite = isOffsite && !kind.isInherentlyOffsite,
                    notes = notes.ifBlank { null },
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = label,
            onValueChange = { label = it },
            label = "What do you call it?",
            placeholder = "Red Samsung T7",
            help = "The name you would say out loud when asking someone to fetch it.",
        )

        YTDropdownField(
            label = "What kind?",
            selected = kind,
            options = StorageKind.entries,
            optionLabel = { it.label },
            onSelect = { kind = it },
            help =
                "The 3-2-1 rule asks for two different kinds, because two drives bought " +
                    "together fail the same way.",
        )

        if (!kind.isInherentlyOffsite) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
            ) {
                Checkbox(
                    checked = isOffsite,
                    onCheckedChange = { isOffsite = it },
                )
                Text(
                    text = "Kept away from the studio",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurface,
                )
            }
        }

        YTTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes",
            placeholder = "In the safe at Mum's",
            imeAction = ImeAction.Done,
        )
    }
}
