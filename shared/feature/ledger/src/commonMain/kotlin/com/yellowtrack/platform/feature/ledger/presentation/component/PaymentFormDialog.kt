package com.yellowtrack.platform.feature.ledger.presentation.component

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
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.feature.ledger.presentation.model.NewPayment
import com.yellowtrack.platform.feature.ledger.presentation.model.OutstandingInvoiceItem
import kotlinx.datetime.LocalDate

/**
 * Records money received against an invoice.
 *
 * The amount is prefilled with the outstanding balance, since paying in full is the
 * common case, but stays editable because part payments are normal — a retainer now and
 * a balance later is how most bookings are actually paid.
 */
@Composable
internal fun PaymentFormDialog(
    invoice: OutstandingInvoiceItem,
    today: LocalDate,
    currency: CurrencyCode,
    prefillAmount: String,
    onSave: (NewPayment) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember(invoice.id) { mutableStateOf(prefillAmount) }
    var paidOn by remember(invoice.id) { mutableStateOf(today.toString()) }
    var method by remember(invoice.id) { mutableStateOf(PaymentMethod.BankTransfer) }
    var reference by remember(invoice.id) { mutableStateOf("") }

    val amountValid = parseMoney(amount, currency)?.isPositive == true
    val dateValid = runCatching { LocalDate.parse(paidOn) }.isSuccess

    YTFormDialog(
        title = "Record a payment",
        supportingText = "${invoice.number} • ${invoice.clientName} • ${invoice.balanceDue} outstanding",
        confirmLabel = "Save payment",
        confirmEnabled = amountValid && dateValid,
        onConfirm = {
            onSave(
                NewPayment(
                    invoiceId = invoice.id,
                    amount = amount.trim(),
                    paidOn = paidOn.trim(),
                    method = method,
                    reference = reference.trim().ifBlank { null },
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount received (${currency.code})",
            keyboardType = KeyboardType.Decimal,
            help = "Leave as-is for payment in full, or reduce it for a part payment.",
            errorMessage = if (amount.isNotBlank() && !amountValid) "Enter an amount such as 1500.00" else null,
        )

        YTTextField(
            value = paidOn,
            onValueChange = { paidOn = it },
            label = "Date received",
            placeholder = "2026-07-28",
            errorMessage = if (paidOn.isNotBlank() && !dateValid) "Use the form 2026-07-28" else null,
        )

        YTDropdownField(
            label = "Method",
            selected = method,
            options = PaymentMethod.entries,
            optionLabel = { it.name },
            onSelect = { method = it },
        )

        YTTextField(
            value = reference,
            onValueChange = { reference = it },
            label = "Reference",
            help = "Bank reference or transaction id, for reconciling later.",
            imeAction = ImeAction.Done,
        )
    }
}
