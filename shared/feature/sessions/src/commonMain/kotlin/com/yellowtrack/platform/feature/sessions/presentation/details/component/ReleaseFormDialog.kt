package com.yellowtrack.platform.feature.sessions.presentation.details.component

import androidx.compose.material3.Text
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
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.release.ReleaseKind
import com.yellowtrack.platform.feature.sessions.presentation.model.NewRelease

/**
 * Records someone photographed, pending their permission.
 *
 * Added as pending rather than signed. "I have their permission" is a claim about a piece
 * of paper that either exists or does not, and starting at signed would let a studio
 * believe it holds something it has never collected.
 */
@Composable
internal fun ReleaseFormDialog(
    onSave: (NewRelease) -> Unit,
    onDismiss: () -> Unit,
) {
    var personName by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ReleaseKind.Adult) }
    var email by remember { mutableStateOf("") }
    var guardianName by remember { mutableStateOf("") }

    YTFormDialog(
        title = "Add someone photographed",
        confirmLabel = "Add",
        confirmEnabled = personName.isNotBlank(),
        onConfirm = {
            onSave(
                NewRelease(
                    personName = personName.trim(),
                    kind = kind,
                    email = email.trim(),
                    guardianName = guardianName.trim(),
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = personName,
            onValueChange = { personName = it },
            label = "Who was photographed?",
            placeholder = "Ada Okafor",
        )

        YTDropdownField(
            label = "Kind of release",
            selected = kind,
            options = ReleaseKind.entries,
            optionLabel = { it.label },
            onSelect = { kind = it },
            optionDescription = { it.explanation },
        )

        if (kind == ReleaseKind.Minor) {
            YTTextField(
                value = guardianName,
                onValueChange = { guardianName = it },
                label = "Parent or guardian",
                help = "A child's release is void without the adult who signed it.",
            )
        }

        YTTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = KeyboardType.Email,
            help = "Where the form goes, and where a copy goes back.",
            imeAction = ImeAction.Done,
        )

        Text(
            text = "Added as pending. Mark it signed once the form actually comes back.",
            style = YTTheme.typography.bodySmall,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}

private val ReleaseKind.label: String
    get() =
        when (this) {
            ReleaseKind.Adult -> "Adult"
            ReleaseKind.Minor -> "Minor"
            ReleaseKind.Property -> "Property"
        }

private val ReleaseKind.explanation: String
    get() =
        when (this) {
            ReleaseKind.Adult -> "Signing for themselves."
            ReleaseKind.Minor -> "A child. A parent or guardian signs."
            ReleaseKind.Property -> "A building or land, signed by whoever owns it."
        }
