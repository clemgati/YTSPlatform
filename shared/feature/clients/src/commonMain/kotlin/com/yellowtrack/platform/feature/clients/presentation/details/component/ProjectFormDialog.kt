package com.yellowtrack.platform.feature.clients.presentation.details.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject

/**
 * Opens a booking against a client.
 *
 * The status defaults to Enquiry rather than Booked, because that is what a booking
 * genuinely is when it is first written down. Booked means something specific in this
 * application — a contract signed and a retainer paid — and a studio that starts every job
 * at Booked loses the one distinction that says which dates are actually held.
 *
 * The contract value is optional and kept on the booking rather than derived from
 * invoices, so that work agreed but not yet billed still reports as pipeline.
 *
 * Pass [initial] to edit a booking that already exists — which is chiefly how a job moves
 * from Enquiry to Booked, the transition the contract and retainer rules are built around.
 */
@Composable
internal fun ProjectFormDialog(
    clientName: String,
    currency: CurrencyCode,
    onSave: (NewProject) -> Unit,
    onDismiss: () -> Unit,
    initial: NewProject? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var serviceLine by remember { mutableStateOf(initial?.serviceLine ?: ServiceLine.Wedding) }
    var status by remember { mutableStateOf(initial?.status ?: ProjectStatus.Enquiry) }
    var contractValue by remember { mutableStateOf(initial?.contractValue.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }

    val valueValid = contractValue.isBlank() || parseMoney(contractValue, currency)?.isPositive == true

    YTFormDialog(
        title = if (initial == null) "Open a booking" else "Edit booking",
        confirmLabel = if (initial == null) "Save" else "Save changes",
        supportingText = "For $clientName",
        confirmEnabled = name.isNotBlank() && valueValid,
        onConfirm = {
            onSave(
                NewProject(
                    name = name.trim(),
                    serviceLine = serviceLine,
                    status = status,
                    contractValue = contractValue.trim(),
                    notes = notes.trim(),
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = name,
            onValueChange = { name = it },
            label = "What is the job?",
            placeholder = "Johnson Wedding",
            help = "The whole booking, not one shoot day.",
        )

        YTDropdownField(
            label = "Kind of work",
            selected = serviceLine,
            options = ServiceLine.entries,
            optionLabel = { it.name },
            onSelect = { serviceLine = it },
        )

        YTDropdownField(
            label = "Where it stands",
            selected = status,
            options = ProjectStatus.entries,
            optionLabel = { it.name },
            onSelect = { status = it },
            optionDescription = { it.explanation },
        )

        YTTextField(
            value = contractValue,
            onValueChange = { contractValue = it },
            label = "Agreed value (${currency.code})",
            keyboardType = KeyboardType.Decimal,
            help = "What the job is worth, even before it is invoiced.",
            errorMessage = if (!valueValid) "Enter an amount such as 4000.00" else null,
        )

        YTTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes",
            singleLine = false,
            imeAction = ImeAction.Done,
        )

        if (status == ProjectStatus.Booked) {
            Text(
                text =
                    "Booked means the contract is signed and the retainer is paid. " +
                        "Until both, the date is not held.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}

private val ProjectStatus.explanation: String
    get() =
        when (this) {
            ProjectStatus.Enquiry -> "Asked about, not yet quoted."
            ProjectStatus.Proposed -> "Quoted and awaiting a decision."
            ProjectStatus.Booked -> "Contract signed and retainer paid. The date is held."
            ProjectStatus.Shooting -> "Shooting has begun."
            ProjectStatus.InPost -> "Culling, editing, and colour."
            ProjectStatus.Delivered -> "Gallery or files handed over."
            ProjectStatus.Complete -> "Delivered, paid in full, archived."
            ProjectStatus.Cancelled -> "Cancelled after booking."
            ProjectStatus.Lost -> "Never booked — went elsewhere or went quiet."
        }
