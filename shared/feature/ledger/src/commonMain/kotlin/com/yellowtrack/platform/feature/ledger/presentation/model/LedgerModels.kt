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
    /**
     * Whether cancelling it is still honest.
     *
     * False once any money has arrived against it: voiding a part-paid invoice would take
     * a payment the studio has actually received out of its books, and the remedy for
     * money received in error is a refund, recorded, not a document quietly cancelled.
     */
    val canVoid: Boolean,
)

/**
 * An invoice raised but never sent.
 *
 * These exist chiefly because accepting a quote raises one, deliberately as a draft so
 * that an unreviewed figure never lands in money owed. They collect nothing until they go
 * out, which makes an unsent invoice the quietest way for agreed work to go unpaid.
 */
internal data class DraftInvoiceItem(
    val id: InvoiceId,
    val number: String,
    val clientName: String,
    val projectName: String,
    val total: String,
    val raisedLabel: String,
)

internal data class MoneyOwedSummary(
    val totalOutstanding: String,
    val overdueAmount: String,
    val overdueCount: Int,
    val invoices: List<OutstandingInvoiceItem>,
    /**
     * Outstanding invoices in some other currency, which the totals above cannot include.
     *
     * They are still listed, each showing its own currency. Only the sum leaves them out,
     * because adding pounds to dollars produces a number that is not money.
     */
    val otherCurrencyCount: Int = 0,
    /** Raised and not yet sent, oldest first — the longest-agreed work goes uncollected. */
    val drafts: List<DraftInvoiceItem> = emptyList(),
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
    /**
     * How much post-production a shoot hour drags behind it.
     *
     * Shown rather than buried, because the whole floor rests on it: a studio that does not
     * know this number is guessing has no reason to distrust a price built from it.
     */
    val postProductionFactor: Double,
    /** True once it comes from the studio's own finished work rather than an assumption. */
    val isFactorMeasured: Boolean,
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
    /**
     * The costs themselves, newest first.
     *
     * Totals were all this screen showed, which meant a studio could record a cost and
     * never see it again: no way to check what was entered, notice the same invoice was
     * entered twice, or itemise anything at the end of the year. A number is not an
     * account of where the money went.
     */
    val items: List<RecordedCost> = emptyList(),
)

/** One cost as it reads on the screen — a journey and an invoice look the same here. */
internal data class RecordedCost(
    val id: String,
    val date: String,
    val description: String,
    val amount: String,
    /**
     * "Overhead", or the booking it is charged to.
     *
     * The consequential field: overhead raises the floor under every job, a job cost comes
     * out of one booking's margin.
     */
    val allocation: String,
    /** The form seeded from this row, so correcting it opens on what was entered. */
    val editable: CostEdit,
)

/**
 * Which form a row reopens.
 *
 * A cost and a journey read the same in the list and are not the same thing to correct —
 * one has an amount, the other a distance and a rate — so the row carries the form rather
 * than the screen guessing from the fields.
 */
internal sealed interface CostEdit {
    data class OfExpense(
        val form: NewExpense,
    ) : CostEdit

    data class OfJourney(
        val form: NewMileage,
    ) : CostEdit
}

/** An enquiry that has been waiting for a reply. */
internal data class WaitingEnquiry(
    val name: String,
    val source: String,
    val waitingLabel: String,
    val isUrgent: Boolean,
)
