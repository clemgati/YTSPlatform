package com.yellowtrack.platform.core.model.quote

import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * What a studio does to a quote once the client has answered.
 *
 * These live as functions rather than as mutable state on [Quote] so that the stamps go
 * on together: a quote that is Accepted with no `acceptedAt` cannot say when the price
 * was agreed, and that date is the one that matters when a client later disputes it.
 */
fun Quote.accepted(at: Instant): Quote =
    copy(status = QuoteStatus.Accepted, acceptedAt = at, declinedAt = null, audit = audit.touched(at))

fun Quote.declined(
    at: Instant,
    reason: String? = null,
): Quote =
    copy(
        status = QuoteStatus.Declined,
        declinedAt = at,
        acceptedAt = null,
        notes = reason?.takeIf(String::isNotBlank) ?: notes,
        audit = audit.touched(at),
    )

fun Quote.sent(
    at: Instant,
    validFor: Duration,
): Quote = copy(status = QuoteStatus.Sent, issuedAt = at, validUntil = at + validFor, audit = audit.touched(at))

/**
 * The invoice that collects an accepted quote.
 *
 * The lines are carried across unchanged, which is the whole reason quotes and invoices
 * share [com.yellowtrack.platform.core.model.billing.LineItem]. Re-entering them by hand
 * is where the figure a client agreed to and the figure they are billed diverge, and the
 * studio only finds out when someone refuses to pay the difference.
 *
 * Raised as a draft rather than sent: converting is a bookkeeping step, and issuing a
 * demand for payment should stay a deliberate one.
 */
fun Quote.toInvoice(
    number: String,
    now: Instant,
    id: InvoiceId = InvoiceId.new(),
    kind: InvoiceKind = InvoiceKind.Full,
    dueAt: Instant? = null,
): Invoice =
    Invoice(
        id = id,
        studioId = studioId,
        projectId = projectId,
        number = number,
        kind = kind,
        status = InvoiceStatus.Draft,
        currency = currency,
        lines = lines,
        dueAt = dueAt,
        notes = notes,
        audit = AuditMetadata.createdAt(now),
    )
