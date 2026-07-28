package com.yellowtrack.platform.feature.ledger.presentation.component

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
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.money.parsePercentageToBasisPoints
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
) {
    val bookings = remember(projects) { projects.filter { it.id != null } }

    var number by remember { mutableStateOf(suggestedNumber) }
    var kind by remember { mutableStateOf(InvoiceKind.Full) }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var taxRate by remember { mutableStateOf("") }
    var dueOn by remember { mutableStateOf(today.plus(DEFAULT_PAYMENT_TERM_DAYS, DateTimeUnit.DAY).toString()) }
    var sendNow by remember { mutableStateOf(true) }
    var selectedProject by remember(bookings) { mutableStateOf(bookings.firstOrNull()) }

    val amountValid = parseMoney(amount, currency)?.isPositive == true
    val taxValid = taxRate.isBlank() || parsePercentageToBasisPoints(taxRate) != null
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
                description.isNotBlank() &&
                amountValid &&
                taxValid &&
                dueValid,
        onConfirm = {
            val projectId = booking?.id ?: return@YTFormDialog

            onSave(
                NewInvoice(
                    number = number.trim(),
                    projectId = projectId,
                    kind = kind,
                    description = description.trim(),
                    amount = amount.trim(),
                    taxRate = taxRate.trim(),
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

        YTTextField(
            value = description,
            onValueChange = { description = it },
            label = "What is it for?",
            placeholder = "Balance of wedding coverage",
        )

        YTTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount (${currency.code})",
            keyboardType = KeyboardType.Decimal,
            errorMessage = if (amount.isNotBlank() && !amountValid) "Enter an amount such as 2500.00" else null,
        )

        YTTextField(
            value = taxRate,
            onValueChange = { taxRate = it },
            label = "Tax rate (%)",
            keyboardType = KeyboardType.Decimal,
            errorMessage = if (!taxValid) "Enter a rate such as 8.25, or leave it blank" else null,
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
