package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.invoice.PaymentState
import com.yellowtrack.platform.feature.ledger.presentation.model.DraftInvoiceItem
import com.yellowtrack.platform.feature.ledger.presentation.model.MoneyOwedSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.OutstandingInvoiceItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ReceivedPayment

/** What the studio is owed, overdue first, in the order the list should be worked. */
@Composable
internal fun MoneyOwedSection(
    summary: MoneyOwedSummary,
    onRecordPayment: (OutstandingInvoiceItem) -> Unit,
    onVoidInvoice: (OutstandingInvoiceItem) -> Unit,
    onSendDraft: (DraftInvoiceItem) -> Unit,
    onDeleteDraft: (DraftInvoiceItem) -> Unit,
    onEditDraft: (DraftInvoiceItem) -> Unit,
    onExportInvoice: (OutstandingInvoiceItem) -> Unit,
    /** Takes a payment off the invoice it was put against; see `ReceivedPayment`. */
    onRemovePayment: (ReceivedPayment) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Money owed",
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium)) {
            Text(
                text = summary.totalOutstanding,
                style = YTTheme.typography.headlineLarge,
                color = YTTheme.colors.onSurface,
            )

            if (summary.hasOverdue) {
                Text(
                    text = "${summary.overdueAmount} overdue across ${summary.overdueCount.invoiceLabel}",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.error,
                )
            } else if (summary.invoices.isNotEmpty()) {
                Text(
                    text = "Nothing overdue",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }

            // Said plainly rather than left as a quietly short total. A studio that has
            // changed what it charges in still has invoices in the old currency, and they
            // cannot be added to the figure above.
            if (summary.otherCurrencyCount > 0) {
                Text(
                    text =
                        "The total leaves out ${summary.otherCurrencyCount.invoiceLabel} " +
                            "in another currency, listed below.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }

            if (summary.invoices.isEmpty()) {
                Text(
                    text = "No unpaid invoices.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                HorizontalDivider(color = YTTheme.colors.outlineVariant)
                summary.invoices.forEach { InvoiceRow(it, onRecordPayment, onVoidInvoice, onExportInvoice) }
            }

            if (summary.drafts.isNotEmpty()) {
                HorizontalDivider(color = YTTheme.colors.outlineVariant)

                Text(
                    text = "Raised but not sent",
                    style = YTTheme.typography.titleSmall,
                    color = YTTheme.colors.onSurface,
                )

                Text(
                    text = "These collect nothing until they go out. Accepting a quote raises one.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                summary.drafts.forEach { DraftRow(it, onSendDraft, onDeleteDraft, onEditDraft) }
            }

            if (summary.received.isNotEmpty()) {
                Text(
                    text = "Payments received",
                    style = YTTheme.typography.titleSmall,
                    color = YTTheme.colors.onSurface,
                )

                Text(
                    text =
                        "Every payment, whatever its invoice now says. One put against the wrong " +
                            "invoice settles it, and a settled invoice leaves the list above — so " +
                            "this is where it can still be found.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                summary.received.forEach { payment -> ReceivedPaymentRow(payment, onRemovePayment) }
            }
        }
    }
}

/**
 * One payment, and the way to take it back off.
 *
 * Removed rather than reassigned: the money arrived and was attributed wrongly, and the
 * honest repair is to take it off this invoice and record it against the right one.
 */
@Composable
private fun ReceivedPaymentRow(
    payment: ReceivedPayment,
    onRemove: (ReceivedPayment) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${payment.amount} · ${payment.against}",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text =
                    listOfNotNull(payment.date, payment.method, payment.reference)
                        .joinToString(" · "),
                style = YTTheme.typography.labelMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        TextButton(onClick = { onRemove(payment) }) {
            Text(
                text = "Remove",
                style = YTTheme.typography.labelLarge,
                color = YTTheme.colors.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftRow(
    draft: DraftInvoiceItem,
    onSend: (DraftInvoiceItem) -> Unit,
    onDelete: (DraftInvoiceItem) -> Unit,
    onEdit: (DraftInvoiceItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = draft.clientName.ifBlank { draft.number },
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text = draft.total,
                style = YTTheme.typography.titleSmall,
                color = YTTheme.colors.onSurface,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    listOfNotNull(
                        draft.number,
                        draft.projectName.ifBlank { null },
                        draft.raisedLabel,
                    ).joinToString(" • "),
                modifier = Modifier.fillMaxWidth(),
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        ) {
            // A draft is a document still being built, so correcting it is the ordinary
            // thing to want. Sent invoices have no such control and are voided instead.
            TextButton(onClick = { onEdit(draft) }) {
                Text(
                    text = "Edit",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.primary,
                )
            }

            TextButton(onClick = { onDelete(draft) }) {
                Text(
                    text = "Discard",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }

            TextButton(onClick = { onSend(draft) }) {
                Text(
                    text = "Send",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

/**
 * One outstanding invoice, with its actions beneath the line that describes it.
 *
 * Beside it they squeezed that line to a word a row on a phone: "INV-004 · Johnson Wedding
 * · 12 days overdue" came out over eight lines, on the screen a studio opens to find out
 * who owes it money.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InvoiceRow(
    invoice: OutstandingInvoiceItem,
    onRecordPayment: (OutstandingInvoiceItem) -> Unit,
    onVoid: (OutstandingInvoiceItem) -> Unit,
    onSave: (OutstandingInvoiceItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = invoice.clientName.ifBlank { invoice.number },
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text = invoice.balanceDue,
                style = YTTheme.typography.titleSmall,
                color = YTTheme.colors.onSurface,
            )
        }

        Text(
            text = invoice.statusLine,
            modifier = Modifier.fillMaxWidth(),
            style = YTTheme.typography.bodyMedium,
            color =
                if (invoice.state == PaymentState.Overdue) {
                    YTTheme.colors.error
                } else {
                    YTTheme.colors.onSurfaceVariant
                },
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        ) {
            // The document the client actually receives. Saved as a file rather than
            // copied as text: an invoice is emailed as an attachment, where a call
            // sheet is pasted into a message.
            TextButton(onClick = { onSave(invoice) }) {
                Text(
                    text = "Save",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }

            // Offered only while nothing has been received: see
            // [OutstandingInvoiceItem.canVoid].
            if (invoice.canVoid) {
                TextButton(onClick = { onVoid(invoice) }) {
                    Text(
                        text = "Void",
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }
            }

            TextButton(onClick = { onRecordPayment(invoice) }) {
                Text(
                    text = "Record payment",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

private val OutstandingInvoiceItem.statusLine: String
    get() =
        buildString {
            append(number)
            if (projectName.isNotBlank()) {
                append(" • ")
                append(projectName)
            }

            when {
                overdueDays != null ->
                    append(" • ${overdueDays.toInt().dayLabel()} overdue")

                state == PaymentState.PartiallyPaid ->
                    append(" • part paid")

                dueLabel != null ->
                    append(" • due $dueLabel")
            }
        }

private fun Int.dayLabel(): String = if (this == 1) "1 day" else "$this days"

private val Int.invoiceLabel: String
    get() = if (this == 1) "1 invoice" else "$this invoices"
