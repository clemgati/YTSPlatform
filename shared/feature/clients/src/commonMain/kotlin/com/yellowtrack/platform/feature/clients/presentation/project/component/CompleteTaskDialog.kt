package com.yellowtrack.platform.feature.clients.presentation.project.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.clients.presentation.project.model.PostTaskItem

/**
 * Closes a piece of work by recording what it actually took.
 *
 * Hours are required rather than optional. A task closed without them tells the pricing
 * floor nothing, and the floor is the only reason any of this is tracked.
 */
@Composable
internal fun CompleteTaskDialog(
    task: PostTaskItem,
    onComplete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var actualHours by remember { mutableStateOf("") }

    val hoursValid = actualHours.trim().toDoubleOrNull()?.let { it > 0 } == true

    YTFormDialog(
        title = "How long did it take?",
        confirmLabel = "Done",
        supportingText = task.name,
        confirmEnabled = hoursValid,
        onConfirm = { onComplete(actualHours.trim()) },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = actualHours,
            onValueChange = { actualHours = it },
            label = "Hours it took",
            placeholder = "5.5",
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
            errorMessage =
                if (actualHours.isNotBlank() && !hoursValid) "Enter hours such as 5 or 5.5" else null,
        )

        task.estimatedLabel?.let { estimate ->
            Text(
                text = "You expected $estimate. The honest figure is the useful one.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}
