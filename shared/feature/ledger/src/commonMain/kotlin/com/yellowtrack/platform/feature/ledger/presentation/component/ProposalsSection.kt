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
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractStage
import com.yellowtrack.platform.feature.ledger.presentation.model.ProposalsSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.QuoteItem

/**
 * Work the studio has priced and not yet been answered on.
 *
 * Sits above pricing and costs because a quote nobody chases is revenue already earned
 * and then abandoned — the same failure as an unpaid invoice, one step earlier.
 */
@Composable
internal fun ProposalsSection(
    summary: ProposalsSummary,
    onNewQuote: () -> Unit,
    onNewInvoice: () -> Unit,
    onNewContract: () -> Unit,
    onAcceptQuote: (QuoteItem) -> Unit,
    onDeclineQuote: (QuoteItem) -> Unit,
    onSendContract: (ContractItem) -> Unit,
    onSignContract: (ContractItem) -> Unit,
    onExportQuote: (QuoteItem) -> Unit,
    onReviseQuote: (QuoteItem) -> Unit,
    onCorrectContract: (ContractItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Out with clients",
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium)) {
            Text(
                text = summary.quotedValue,
                style = YTTheme.typography.headlineLarge,
                color = YTTheme.colors.onSurface,
            )

            Text(
                text =
                    when {
                        summary.hasExpired ->
                            "${summary.expiredCount.quoteLabel} past the date you said the price held"

                        summary.awaitingDecision.isNotEmpty() ->
                            "${summary.awaitingDecision.size.quoteLabel} awaiting a decision"

                        else -> "No quotes awaiting a decision."
                    },
                style = YTTheme.typography.bodyMedium,
                color = if (summary.hasExpired) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
            )

            if (summary.awaitingDecision.isNotEmpty()) {
                HorizontalDivider(color = YTTheme.colors.outlineVariant)
                summary.awaitingDecision.forEach { quote ->
                    QuoteRow(quote, onAcceptQuote, onDeclineQuote, onExportQuote, onReviseQuote)
                }
            }

            if (summary.datesNotHeld.isNotEmpty()) {
                HorizontalDivider(color = YTTheme.colors.outlineVariant)

                Text(
                    text = "Dates not yet held",
                    style = YTTheme.typography.titleSmall,
                    color = YTTheme.colors.onSurface,
                )

                Text(
                    text = "Until these are signed and the retainer is paid, the date is still for sale.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                summary.datesNotHeld.forEach { ContractRow(it, onSendContract, onSignContract, onCorrectContract) }
            }

            HorizontalDivider(color = YTTheme.colors.outlineVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
                TextButton(onClick = onNewQuote) {
                    Text(
                        text = "Send a quote",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }

                TextButton(onClick = onNewContract) {
                    Text(
                        text = "Draw up a contract",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }

                TextButton(onClick = onNewInvoice) {
                    Text(
                        text = "Raise an invoice",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuoteRow(
    quote: QuoteItem,
    onAccept: (QuoteItem) -> Unit,
    onDecline: (QuoteItem) -> Unit,
    onSave: (QuoteItem) -> Unit,
    onRevise: (QuoteItem) -> Unit,
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
                text = quote.clientName.ifBlank { quote.number },
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text = quote.total,
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
                text = quote.statusLine,
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyMedium,
                color = if (quote.isExpired) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
            )

            Row {
                // Every quote listed here is still awaiting a decision, so every one of
                // them can be revised. Once answered it leaves this list, which is also the
                // point at which it stops being editable.
                TextButton(onClick = { onRevise(quote) }) {
                    Text(
                        text = "Revise",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }

                // The document the client is sent, beside the two answers it can come
                // back with.
                TextButton(onClick = { onSave(quote) }) {
                    Text(
                        text = "Save",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                TextButton(onClick = { onDecline(quote) }) {
                    Text(
                        text = "Declined",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                TextButton(onClick = { onAccept(quote) }) {
                    Text(
                        text = "Accepted",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContractRow(
    contract: ContractItem,
    onSend: (ContractItem) -> Unit,
    onSign: (ContractItem) -> Unit,
    onCorrect: (ContractItem) -> Unit,
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
                text = contract.clientName.ifBlank { contract.title },
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )

            contract.retainer?.let { retainer ->
                Text(
                    text = "$retainer retainer",
                    style = YTTheme.typography.titleSmall,
                    color = YTTheme.colors.onSurface,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    listOfNotNull(contract.title, contract.stageLabel, contract.waitingLabel)
                        .joinToString(" • "),
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )

            Row {
                // Absent once signed. The words are what somebody agreed to, and this list
                // still shows a signed contract while its retainer is outstanding.
                if (contract.canEdit) {
                    TextButton(onClick = { onCorrect(contract) }) {
                        Text(
                            text = "Correct",
                            style = YTTheme.typography.labelLarge,
                            color = YTTheme.colors.primary,
                        )
                    }
                }

                if (contract.canSend) {
                    TextButton(onClick = { onSend(contract) }) {
                        Text(
                            text = "Send",
                            style = YTTheme.typography.labelLarge,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }
                }

                if (contract.canSign) {
                    TextButton(onClick = { onSign(contract) }) {
                        Text(
                            text = "Signed",
                            style = YTTheme.typography.labelLarge,
                            color = YTTheme.colors.primary,
                        )
                    }
                }
            }
        }

        // A signed contract waiting only on money says where that money is collected,
        // rather than leaving the studio to wonder why the row will not go away.
        if (contract.stage == ContractStage.AwaitingRetainer) {
            Text(
                text = "Record the retainer against its invoice, above, and this clears.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}

private val QuoteItem.statusLine: String
    get() =
        buildString {
            append(number)
            if (projectName.isNotBlank()) {
                append(" • ")
                append(projectName)
            }

            when {
                isExpired && validUntilLabel != null -> append(" • held only until $validUntilLabel")
                isExpired -> append(" • expired")
                waitingLabel != null -> append(" • $waitingLabel")
            }
        }

private val Int.quoteLabel: String
    get() = if (this == 1) "1 quote" else "$this quotes"
