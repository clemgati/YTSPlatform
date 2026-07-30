package com.yellowtrack.platform.feature.clients.presentation.project.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.model.post.PostTaskKind
import com.yellowtrack.platform.feature.clients.presentation.project.model.NewPostTask

/**
 * Adds a piece of post-production, with what it is expected to take.
 *
 * The estimate is asked for now rather than when the work is finished. An estimate written
 * afterwards is a memory of how long it felt, and it agrees with the actual every time —
 * which makes the comparison worthless and the pricing floor no better informed.
 */
@Composable
internal fun PostTaskFormDialog(
    onSave: (NewPostTask) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(PostTaskKind.Edit) }
    var estimatedHours by remember { mutableStateOf("") }

    val estimateValid =
        estimatedHours.isBlank() || (estimatedHours.trim().toDoubleOrNull()?.let { it > 0 } == true)

    YTFormDialog(
        title = "Add post-production work",
        confirmLabel = "Add",
        confirmEnabled = name.isNotBlank() && estimateValid,
        onConfirm = {
            onSave(
                NewPostTask(
                    name = name.trim(),
                    kind = kind,
                    estimatedHours = estimatedHours.trim(),
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = name,
            onValueChange = { name = it },
            label = "What is the work?",
            placeholder = "Cull the wedding day",
        )

        YTDropdownField(
            label = "Kind",
            selected = kind,
            options = PostTaskKind.entries,
            optionLabel = { it.label },
            onSelect = { kind = it },
            optionDescription = { it.explanation },
        )

        YTTextField(
            value = estimatedHours,
            onValueChange = { estimatedHours = it },
            label = "Hours you expect",
            placeholder = "4",
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            help = "Guess now. Comparing it to the real figure later is the whole point.",
            errorMessage = if (!estimateValid) "Enter hours such as 4 or 2.5" else null,
        )
    }
}

private val PostTaskKind.label: String
    get() =
        when (this) {
            PostTaskKind.Cull -> "Cull"
            PostTaskKind.Edit -> "Edit"
            PostTaskKind.Colour -> "Colour"
            PostTaskKind.Retouch -> "Retouch"
            PostTaskKind.AlbumDesign -> "Album design"
            PostTaskKind.Delivery -> "Delivery"
            PostTaskKind.Admin -> "Admin"
            PostTaskKind.Other -> "Other"
        }

private val PostTaskKind.explanation: String
    get() =
        when (this) {
            PostTaskKind.Cull -> "Choosing which frames survive. Almost always underestimated."
            PostTaskKind.Edit -> "The main edit."
            PostTaskKind.Colour -> "Grading and matching."
            PostTaskKind.Retouch -> "Skin, objects, removals."
            PostTaskKind.AlbumDesign -> "Laying out an album."
            PostTaskKind.Delivery -> "Exporting, uploading, getting it in front of the client."
            PostTaskKind.Admin -> "Emails, invoicing, chasing. Unbilled and rarely counted."
            PostTaskKind.Other -> "Anything else after the shoot."
        }
