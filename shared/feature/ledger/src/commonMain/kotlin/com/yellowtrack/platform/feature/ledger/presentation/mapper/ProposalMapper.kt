package com.yellowtrack.platform.feature.ledger.presentation.mapper

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.sum
import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ProposalsSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.QuoteItem
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

internal fun buildProposals(
    quotes: List<Quote>,
    contracts: List<Contract>,
    invoices: List<Invoice>,
    projects: List<Project>,
    clients: List<Client>,
    now: Instant,
    currency: CurrencyCode,
): ProposalsSummary {
    val projectsById = projects.associateBy { it.id }
    val clientsById = clients.associateBy { it.id }
    val zone = TimeZone.currentSystemDefault()

    fun clientNameFor(projectId: ProjectId): String =
        projectsById[projectId]?.let { clientsById[it.clientId]?.displayName }.orEmpty()

    // Expired first: a lapsed quote is the one needing a decision today, either to chase
    // it or to let it go.
    val open =
        quotes
            .filter { it.status.isAwaitingDecision }
            .sortedWith(
                compareByDescending<Quote> { it.isExpired(now) }
                    .thenBy { it.issuedAt ?: it.audit.createdAt },
            )

    val quoteItems =
        open.map { quote ->
            QuoteItem(
                id = quote.id,
                number = quote.number,
                clientName = clientNameFor(quote.projectId),
                projectName = projectsById[quote.projectId]?.name.orEmpty(),
                total = quote.total.display(),
                status = quote.effectiveStatus(now),
                waitingLabel = quote.issuedAt?.let { waitingLabel(it, now) },
                validUntilLabel = quote.validUntil?.let { DateFormats.shortDate(it, zone) },
            )
        }

    val contractItems =
        contracts
            .filter { it.status == ContractStatus.Sent }
            .sortedBy { it.sentAt ?: it.audit.createdAt }
            .map { contract ->
                ContractItem(
                    id = contract.id,
                    title = contract.title,
                    clientName = clientNameFor(contract.projectId),
                    retainer = contract.retainerAmount?.display(),
                    waitingLabel = contract.sentAt?.let { waitingLabel(it, now) },
                )
            }

    return ProposalsSummary(
        awaitingDecision = quoteItems,
        awaitingSignature = contractItems,
        quotedValue =
            open
                .map(Quote::total)
                .filter { it.currency == currency }
                .sum(currency)
                .display(),
        expiredCount = open.count { it.isExpired(now) },
        nextQuoteNumber = nextNumber(QUOTE_PREFIX, quotes.map(Quote::number)),
        nextInvoiceNumber = nextNumber(INVOICE_PREFIX, invoices.map(Invoice::number)),
    )
}

internal const val QUOTE_PREFIX = "QUO-"

internal const val INVOICE_PREFIX = "INV-"

private const val DEFAULT_NUMBER_WIDTH = 3

/**
 * Suggests the next document number by continuing the highest sequence already used.
 *
 * Derived from the rows that exist rather than from a stored counter, because a counter
 * and the documents it is supposed to describe drift apart the moment one is deleted or
 * arrives from another device. Numbers in another scheme are ignored rather than
 * renumbered: a studio that types its own keeps it, and simply gets a suggestion it can
 * overwrite.
 */
internal fun nextNumber(
    prefix: String,
    existing: List<String>,
): String {
    val sequences =
        existing.mapNotNull { number ->
            number
                .takeIf { it.startsWith(prefix, ignoreCase = true) }
                ?.drop(prefix.length)
                ?.toIntOrNull()
                ?.takeIf { it >= 0 }
        }

    val next = (sequences.maxOrNull() ?: 0) + 1
    val width = maxOf(DEFAULT_NUMBER_WIDTH, sequences.maxOfOrNull { it.toString().length } ?: 0)

    return prefix + next.toString().padStart(width, '0')
}

/** How long a document has been sitting with a client, in the terms a studio thinks in. */
private fun waitingLabel(
    since: Instant,
    now: Instant,
): String {
    val days = (now - since).inWholeDays

    return when {
        days <= 0L -> "sent today"
        days == 1L -> "sent yesterday"
        days < 14L -> "sent $days days ago"
        days < 60L -> "sent ${days / 7} weeks ago"
        else -> "sent ${days / 30} months ago"
    }
}
