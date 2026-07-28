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
    onAcceptQuote: (QuoteItem) -> Unit,
    onDeclineQuote: (QuoteItem) -> Unit,
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
                    QuoteRow(quote, onAcceptQuote, onDeclineQuote)
                }
            }

            if (summary.awaitingSignature.isNotEmpty()) {
                HorizontalDivider(color = YTTheme.colors.outlineVariant)

                Text(
                    text = "Awaiting signature",
                    style = YTTheme.typography.titleSmall,
                    color = YTTheme.colors.onSurface,
                )

                Text(
                    text = "Until these are signed and the retainer is paid, no date is held.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                summary.awaitingSignature.forEach { ContractRow(it) }
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
private fun ContractRow(contract: ContractItem) {
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

        Text(
            text = listOfNotNull(contract.title, contract.waitingLabel).joinToString(" • "),
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )
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
