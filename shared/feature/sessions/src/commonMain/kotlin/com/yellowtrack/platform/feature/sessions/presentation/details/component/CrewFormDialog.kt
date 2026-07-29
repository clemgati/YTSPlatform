package com.yellowtrack.platform.feature.sessions.presentation.details.component

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
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.feature.sessions.presentation.model.NewCrewMember
import com.yellowtrack.platform.feature.sessions.presentation.model.label
import kotlinx.datetime.LocalTime

/**
 * Adds someone to the day.
 *
 * A phone number is asked for because a call sheet with names and no numbers is a list of
 * people you cannot reach on the one morning you need to.
 */
@Composable
internal fun CrewFormDialog(
    sessionCallTime: String?,
    onSave: (NewCrewMember) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(CrewRole.SecondShooter) }
    var phone by remember { mutableStateOf("") }
    var callTime by remember { mutableStateOf("") }

    val callTimeValid =
        callTime.isBlank() || runCatching { LocalTime.parse(callTime.trim()) }.isSuccess

    YTFormDialog(
        title = "Add someone to the day",
        confirmLabel = "Add",
        confirmEnabled = name.isNotBlank() && callTimeValid,
        onConfirm = {
            onSave(
                NewCrewMember(
                    name = name.trim(),
                    role = role,
                    phone = phone.trim(),
                    callTime = callTime.trim(),
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            placeholder = "Priya Shah",
        )

        YTDropdownField(
            label = "Role",
            selected = role,
            options = CrewRole.entries,
            optionLabel = { it.label },
            onSelect = { role = it },
            optionDescription = { it.explanation },
        )

        YTTextField(
            value = phone,
            onValueChange = { phone = it },
            label = "Phone",
            keyboardType = KeyboardType.Phone,
            help = "The number to ring when they are not where they said they would be.",
        )

        YTTextField(
            value = callTime,
            onValueChange = { callTime = it },
            label = "Called at",
            placeholder = "09:00",
            imeAction = ImeAction.Done,
            help =
                sessionCallTime
                    ?.let { "Leave blank and they are due with the crew, $it." }
                    ?: "Leave blank and they are due when the shoot starts.",
            errorMessage = if (!callTimeValid) "Use a 24-hour time such as 09:00" else null,
        )
    }
}

private val CrewRole.explanation: String
    get() =
        when (this) {
            CrewRole.SecondShooter -> "Shooting alongside you."
            CrewRole.Assistant -> "Carrying, lighting, wrangling."
            CrewRole.Videographer -> "Filming. Usually arrives after you."
            CrewRole.MakeUp -> "Called first and gone by the ceremony."
            CrewRole.Stylist -> "Dressing the shoot or the people in it."
            CrewRole.Planner -> "Runs the schedule everyone else follows."
            CrewRole.Venue -> "Who to ring when nobody answers the door."
            CrewRole.Other -> "Anyone else on the day."
        }
