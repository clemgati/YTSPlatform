package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.feature.ledger.presentation.model.NewInvoice
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Raises a demand for payment.
 *
 * "Send it now" is the consequential control and defaults to on, because an invoice left
 * as a draft collects nothing and is the quietest way for a studio to go unpaid. A draft
 * is still available for the case where the figure needs checking first.
 */
@Composable
internal fun InvoiceFormDialog(
    suggestedNumber: String,
    today: LocalDate,
    currency: CurrencyCode,
    projects: List<ProjectOption>,
    onSave: (NewInvoice) -> Unit,
    onDismiss: () -> Unit,
    /** The draft being corrected, or null when this is a new invoice. */
    initial: NewInvoice? = null,
) {
    val bookings = remember(projects) { projects.filter { it.id != null } }

    var number by remember { mutableStateOf(initial?.number ?: suggestedNumber) }
    var kind by remember { mutableStateOf(initial?.kind ?: InvoiceKind.Full) }
    var dueOn by
        remember {
            mutableStateOf(
                initial?.dueOn?.ifBlank { null }
                    ?: today.plus(DEFAULT_PAYMENT_TERM_DAYS, DateTimeUnit.DAY).toString(),
            )
        }

    // False for a correction whatever the draft was raised as: sending is an instruction
    // about what to do now, not a property of the document being corrected.
    var sendNow by remember { mutableStateOf(initial == null) }
    var selectedProject by
        remember(bookings) {
            mutableStateOf(
                bookings.firstOrNull { it.id == initial?.projectId } ?: bookings.firstOrNull(),
            )
        }

    val lines =
        remember {
            mutableStateListOf(
                *initial
                    ?.lines
                    .orEmpty()
                    .map(LineFields::of)
                    .ifEmpty { listOf(LineFields()) }
                    .toTypedArray(),
            )
        }

    val dueValid = runCatching { LocalDate.parse(dueOn) }.isSuccess
    val booking = selectedProject

    YTFormDialog(
        title = "Raise an invoice",
        confirmLabel = if (sendNow) "Send" else "Save draft",
        supportingText =
            if (bookings.isEmpty()) {
                "An invoice is raised against a booking, and there are none yet."
            } else {
                null
            },
        confirmEnabled =
            booking?.id != null &&
                number.isNotBlank() &&
                lines.allValid(currency) &&
                dueValid,
        onConfirm = {
            val projectId = booking?.id ?: return@YTFormDialog

            onSave(
                NewInvoice(
                    number = number.trim(),
                    projectId = projectId,
                    kind = kind,
                    lines = lines.map(LineFields::asNew),
                    dueOn = dueOn.trim(),
                    sendNow = sendNow,
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        if (booking != null) {
            YTDropdownField(
                label = "For",
                selected = booking,
                options = bookings,
                optionLabel = ProjectOption::label,
                onSelect = { selectedProject = it },
            )
        }

        YTTextField(
            value = number,
            onValueChange = { number = it },
            label = "Invoice number",
        )

        YTDropdownField(
            label = "Kind",
            selected = kind,
            options = InvoiceKind.entries,
            optionLabel = { it.name },
            onSelect = { kind = it },
            optionDescription = { it.explanation },
        )

        LineItemsEditor(
            lines = lines,
            currency = currency,
            onChange = { index, updated -> lines[index] = updated },
            onAdd = { lines.add(LineFields()) },
            onRemove = { index -> lines.removeAt(index) },
        )

        YTTextField(
            value = dueOn,
            onValueChange = { dueOn = it },
            label = "Due",
            placeholder = today.toString(),
            errorMessage = if (dueOn.isNotBlank() && !dueValid) "Use the form $today" else null,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        ) {
            Checkbox(
                checked = sendNow,
                onCheckedChange = { sendNow = it },
            )
            Text(
                text = "Send it now",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurface,
            )
        }

        Text(
            text =
                if (sendNow) {
                    "Counts toward money owed, and goes overdue after the due date."
                } else {
                    "A draft owes nothing and never goes overdue. It will not appear in money owed."
                },
            style = YTTheme.typography.bodySmall,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}

private val InvoiceKind.explanation: String
    get() =
        when (this) {
            InvoiceKind.Retainer -> "Secures the date. Usually non-refundable."
            InvoiceKind.Balance -> "The remainder, due before or shortly after the shoot."
            InvoiceKind.Full -> "The whole fee in one go."
            InvoiceKind.Additional -> "Overages, extra hours, or a licence renewal."
        }

/** Matches the terms an invoice raised from an accepted quote is given. */
private const val DEFAULT_PAYMENT_TERM_DAYS = 14
