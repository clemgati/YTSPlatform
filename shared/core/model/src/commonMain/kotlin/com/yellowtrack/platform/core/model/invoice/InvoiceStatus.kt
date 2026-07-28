package com.yellowtrack.platform.core.model.invoice

import kotlinx.serialization.Serializable

/**
 * The part of an invoice's state a person controls.
 *
 * Deliberately small. Whether an invoice is paid, part-paid, or overdue is *derived* from
 * its payments and due date — see [PaymentState] — rather than stored. A stored "Paid"
 * flag and the payments that are supposed to justify it drift apart the first time
 * someone records a payment through a different path, and then the books are wrong in a
 * way nobody notices until a client is chased for money they already sent.
 */
@Serializable
enum class InvoiceStatus {
    /** Not yet sent. Does not count toward money owed. */
    Draft,

    Sent,

    /** Cancelled. Retained for the audit trail rather than deleted. */
    Void,
}

/** What a stored invoice plus its payments actually means, as of a given instant. */
enum class PaymentState {
    Draft,
    Void,

    /** Sent, nothing received, not yet due. */
    AwaitingPayment,

    PartiallyPaid,
    Paid,

    /** Sent, still owed, past its due date. The list every studio should be working from. */
    Overdue,
    ;

    val isOutstanding: Boolean get() = this in setOf(AwaitingPayment, PartiallyPaid, Overdue)
}

/** What the invoice is for, which determines when it is issued and what it collects. */
@Serializable
enum class InvoiceKind {
    /** Secures the date. Usually non-refundable, and what makes a booking real. */
    Retainer,

    /** The remainder, typically due before or shortly after the shoot. */
    Balance,

    /** The whole fee in one go. */
    Full,

    /** Overages, extra hours, additional deliverables, or a usage-licence renewal. */
    Additional,
}
