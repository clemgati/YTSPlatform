package com.yellowtrack.platform.feature.ledger.presentation.model

import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.PaymentState
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus

/** An invoice as the chase list needs it. */
internal data class OutstandingInvoiceItem(
    val id: InvoiceId,
    val number: String,
    val clientName: String,
    val projectName: String,
    val balanceDue: String,
    /**
     * The same amount without currency or formatting, for prefilling the payment form.
     *
     * Carried explicitly rather than recovered by splitting [balanceDue] — a display
     * string is not a parseable value, and coupling one to the other breaks silently the
     * first time formatting changes.
     */
    val balanceDuePlain: String,
    val state: PaymentState,
    /** Null unless overdue. */
    val overdueDays: Long?,
    val dueLabel: String?,
)

internal data class MoneyOwedSummary(
    val totalOutstanding: String,
    val overdueAmount: String,
    val overdueCount: Int,
    val invoices: List<OutstandingInvoiceItem>,
) {
    val hasOverdue: Boolean get() = overdueCount > 0
}

/**
 * The pricing floor, with its working shown.
 *
 * Null when the studio has not yet stated a salary target and billable days — the two
 * figures the calculation cannot infer.
 */
internal data class PricingSummary(
    val costPerBillableDay: String,
    val annualOverhead: String,
    val targetSalary: String,
    val taxAllowance: String,
    val totalAnnualRequirement: String,
    val billableDaysPerYear: Int,
    val packages: List<PackagePricing>,
) {
    val underpricedPackages: List<PackagePricing> get() = packages.filter(PackagePricing::isBelowCost)
}

/** A service template measured against the floor. */
internal data class PackagePricing(
    val name: String,
    val serviceLine: String,
    val price: String,
    val minimumPrice: String,
    val difference: String,
    val estimatedDays: String,
    val isBelowCost: Boolean,
    val hasPrice: Boolean,
)

/** A quote as the follow-up list needs it. */
internal data class QuoteItem(
    val id: QuoteId,
    val number: String,
    val clientName: String,
    val projectName: String,
    val total: String,
    /** The state to act on, which for a lapsed quote is Expired rather than what is stored. */
    val status: QuoteStatus,
    val waitingLabel: String?,
    val validUntilLabel: String?,
) {
    val isExpired: Boolean get() = status == QuoteStatus.Expired
}

/**
 * How far a contract has got towards actually holding a date.
 *
 * Ordered by how the studio should read the list: what is stuck on its own desk first,
 * then what is with the client, then what is waiting only on money.
 */
internal enum class ContractStage {
    /** Drawn up and never sent. Nobody is waiting on the client for this one. */
    NotSent,
    AwaitingSignature,

    /** Signed, but the retainer that holds the date has not been paid. */
    AwaitingRetainer,
}

/** A contract that does not yet hold its date. */
internal data class ContractItem(
    val id: ContractId,
    val title: String,
    val clientName: String,
    val retainer: String?,
    val stage: ContractStage,
    val waitingLabel: String?,
) {
    val canSend: Boolean get() = stage == ContractStage.NotSent

    val canSign: Boolean get() = stage != ContractStage.AwaitingRetainer

    val stageLabel: String
        get() =
            when (stage) {
                ContractStage.NotSent -> "not sent yet"
                ContractStage.AwaitingSignature -> "awaiting signature"
                ContractStage.AwaitingRetainer -> "signed, retainer unpaid"
            }
}

/**
 * What the studio has out with clients and has not had an answer to.
 *
 * Kept beside money owed because it is the same question one step earlier: an unanswered
 * quote and an unpaid invoice are both revenue the studio has already done the work to
 * earn and has not collected.
 */
internal data class ProposalsSummary(
    val awaitingDecision: List<QuoteItem>,
    /**
     * Contracts that do not yet hold their date — unsent, unsigned, or signed with the
     * retainer still outstanding. A signature alone does not hold a date, so a contract
     * does not leave this list until the money that holds it has arrived.
     */
    val datesNotHeld: List<ContractItem>,
    /** The total value of quotes still out, at their quoted figure. */
    val quotedValue: String,
    val expiredCount: Int,
    /** Continues the studio's own numbering rather than restarting at one. */
    val nextQuoteNumber: String,
    val nextInvoiceNumber: String,
) {
    val hasExpired: Boolean get() = expiredCount > 0
}

internal data class ExpenseSummary(
    val year: Int,
    val overheadTotal: String,
    val jobCostTotal: String,
    val mileageDeduction: String,
    val recorded: Int,
)

/** An enquiry that has been waiting for a reply. */
internal data class WaitingEnquiry(
    val name: String,
    val source: String,
    val waitingLabel: String,
    val isUrgent: Boolean,
)
