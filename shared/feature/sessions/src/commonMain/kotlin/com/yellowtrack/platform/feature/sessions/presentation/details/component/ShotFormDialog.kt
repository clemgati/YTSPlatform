package com.yellowtrack.platform.feature.sessions.presentation.details.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.feature.sessions.presentation.model.NewShot

/**
 * Adds one promised photograph.
 *
 * The group is asked for before the people, because the group decides when the shot gets
 * taken and therefore who has to still be standing there when it does. It is prefilled
 * with the group already being worked, since shots are written down in runs.
 */
@Composable
internal fun ShotFormDialog(
    knownGroups: List<String>,
    onSave: (NewShot) -> Unit,
    onDismiss: () -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(knownGroups.lastOrNull().orEmpty()) }
    var people by remember { mutableStateOf("") }

    YTFormDialog(
        title = "Add a shot",
        confirmLabel = "Add",
        confirmEnabled = description.isNotBlank(),
        onConfirm = {
            onSave(
                NewShot(
                    description = description.trim(),
                    group = group.trim(),
                    people = people.trim(),
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = description,
            onValueChange = { description = it },
            label = "What is the photograph?",
            placeholder = "Bride with her grandmother",
        )

        YTTextField(
            value = group,
            onValueChange = { group = it },
            label = "Group",
            placeholder = "Bride's family",
            help =
                if (knownGroups.isEmpty()) {
                    "Shots are worked a group at a time, so a group can be let go once it is done."
                } else {
                    "Already on this day: ${knownGroups.joinToString(", ")}"
                },
        )

        YTTextField(
            value = people,
            onValueChange = { people = it },
            label = "Who is needed",
            placeholder = "Grandma Ruth + the twins",
            help = "As you would call for them across a lawn.",
            imeAction = ImeAction.Done,
        )
    }
}
