package com.yellowtrack.platform.feature.clients.presentation.project.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.delivery.DeliverableKind
import com.yellowtrack.platform.feature.clients.presentation.project.model.NewDeliverable

/**
 * Promises something to the client.
 *
 * No due date is asked for. It is the last shoot day plus the turnaround the contract
 * already promises, and asking a studio to work that out by hand is asking it to get its
 * own deadline wrong.
 */
@Composable
internal fun DeliverableFormDialog(
    promiseNote: String,
    onSave: (NewDeliverable) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(DeliverableKind.Gallery) }

    YTFormDialog(
        title = "Promise something",
        confirmLabel = "Add",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onSave(NewDeliverable(name = name.trim(), kind = kind)) },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = name,
            onValueChange = { name = it },
            label = "What is promised?",
            placeholder = "Full gallery",
            imeAction = ImeAction.Done,
        )

        YTDropdownField(
            label = "Kind",
            selected = kind,
            options = DeliverableKind.entries,
            optionLabel = { it.label },
            onSelect = { kind = it },
        )

        Text(
            text = promiseNote,
            style = YTTheme.typography.bodySmall,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}

private val DeliverableKind.label: String
    get() =
        when (this) {
            DeliverableKind.Gallery -> "Gallery"
            DeliverableKind.Album -> "Album"
            DeliverableKind.Prints -> "Prints"
            DeliverableKind.Video -> "Video"
            DeliverableKind.RawFiles -> "Files"
            DeliverableKind.Other -> "Other"
        }
