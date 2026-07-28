package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.yellowtrack.platform.feature.ledger.presentation.model.MoneyOwedSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.OutstandingInvoiceItem

/** What the studio is owed, overdue first, in the order the list should be worked. */
@Composable
internal fun MoneyOwedSection(
    summary: MoneyOwedSummary,
    onRecordPayment: (OutstandingInvoiceItem) -> Unit,
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

            if (summary.invoices.isEmpty()) {
                Text(
                    text = "No unpaid invoices.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                HorizontalDivider(color = YTTheme.colors.outlineVariant)
                summary.invoices.forEach { InvoiceRow(it, onRecordPayment) }
            }
        }
    }
}

@Composable
private fun InvoiceRow(
    invoice: OutstandingInvoiceItem,
    onRecordPayment: (OutstandingInvoiceItem) -> Unit,
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
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text = invoice.balanceDue,
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
                text = invoice.statusLine,
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyMedium,
                color =
                    if (invoice.state == PaymentState.Overdue) {
                        YTTheme.colors.error
                    } else {
                        YTTheme.colors.onSurfaceVariant
                    },
            )

            TextButton(onClick = { onRecordPayment(invoice) }) {
                Text(
                    text = "Record payment",
                    style = YTTheme.typography.labelLarge,
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
