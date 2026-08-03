package com.yellowtrack.platform.feature.ledger.presentation.model

import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.LicenseMedium
import com.yellowtrack.platform.core.model.expense.DistanceUnit
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.service.ServiceLine

/**
 * What the expense form collected.
 *
 * Amounts stay as text; parsing is the ViewModel's job so the rule about what counts as a
 * valid amount lives in one place.
 */
internal data class NewExpense(
    val description: String,
    val amount: String,
    val category: ExpenseCategory,
    val incurredOn: String,
    /** Null means overhead, which is what feeds the pricing floor. */
    val projectId: ProjectId?,
    val vendor: String?,
    val isTaxDeductible: Boolean,
)

/**
 * What the journey form collected.
 *
 * Distance and rate stay as text for the reason amounts do: what counts as readable is the
 * ViewModel's rule, in one place, rather than something the form decides for itself.
 */
internal data class NewMileage(
    val travelledOn: String,
    val distance: String,
    val unit: DistanceUnit,
    val ratePerUnit: String,
    /** Null means overhead, the same as it does for a cost. */
    val projectId: ProjectId?,
    val purpose: String?,
    val fromLocation: String?,
    val toLocation: String?,
)

/**
 * What the package form collected.
 *
 * Numbers stay as text for the reason amounts do elsewhere: a half-typed figure is a
 * legitimate state to be in, and a form that refuses to hold one fights the person filling
 * it in. They are parsed once, on save.
 *
 * A blank price is allowed and means something — a package a studio has not decided a
 * figure for yet still has a floor, and seeing that floor is how the figure gets decided.
 */
internal data class NewServiceTemplate(
    val name: String,
    val serviceLine: ServiceLine,
    val sessionDurationMinutes: String,
    val sessionCount: String,
    val basePrice: String,
    val deliverableCount: String,
    val turnaroundDays: String,
    val revisionRounds: String,
    val notes: String,
)

internal data class NewPayment(
    val invoiceId: InvoiceId,
    val amount: String,
    val paidOn: String,
    val method: PaymentMethod,
    val reference: String?,
)

/**
 * One billable line as the form collected it.
 *
 * [quantity] is text like the rest: a quantity that will not parse is a typo to be shown
 * back, not a silent one. It defaults to a single unit, which is what most lines are.
 */
internal data class NewLineItem(
    val description: String,
    val quantity: String = "1",
    val unitPrice: String,
    /** Blank means no tax on this line, which is common and not an omission. */
    val taxRate: String = "",
)

/**
 * What the quote form collected.
 *
 * Several lines, because that is how work is actually priced: coverage, a second shooter,
 * and an album are three figures a client wants to see separately, and collapsing them
 * into one total is how a studio loses the argument about what was included.
 */
internal data class NewQuote(
    val number: String,
    val projectId: ProjectId,
    val lines: List<NewLineItem>,
    /** Blank means no expiry is set, which the form warns about rather than forbids. */
    val validUntil: String,
    val terms: String?,
)

internal data class NewInvoice(
    val number: String,
    val projectId: ProjectId,
    val kind: InvoiceKind,
    val lines: List<NewLineItem>,
    val dueOn: String,
    /**
     * Whether to issue it now.
     *
     * A draft owes nothing and cannot go overdue, so raising one is safe; sending is the
     * step that puts a figure into money owed, and it stays deliberate.
     */
    val sendNow: Boolean,
)

/**
 * What the contract form collected.
 *
 * The terms carry defaults rather than opening blank: a photographer asked to compose a
 * cancellation clause inside a dialog will leave it empty, and an empty clause is the one
 * that loses the argument. Every default is editable text, not a hidden rule.
 */
internal data class NewContract(
    val projectId: ProjectId,
    val title: String,
    /** Blank means no retainer, which means nothing yet holds the date. */
    val retainerAmount: String,
    val isRetainerRefundable: Boolean,
    val turnaroundDays: String,
    val revisionRounds: String,
    val cancellationTerms: String?,
    val rescheduleTerms: String?,
    val weatherClause: String?,
    /** Null for the ordinary portrait or wedding job, which licenses nothing. */
    val license: NewUsageLicense?,
    /** Sending is the step that puts it in front of a client, and stays deliberate. */
    val sendNow: Boolean,
)

/**
 * What the licence half of the contract form collected.
 *
 * [durationMonths] is blank for perpetual, which the form warns about: a perpetual grant
 * forecloses every future fee from the same work and should be priced, not defaulted into.
 */
internal data class NewUsageLicense(
    val media: Set<LicenseMedium>,
    val territory: String,
    val durationMonths: String,
    val isExclusive: Boolean,
    val startsOn: String,
)

/**
 * What the signature form collected.
 *
 * The signer is recorded by name because a contract signed by nobody in particular is not
 * evidence of anything, and the date is asked for rather than assumed: contracts are
 * routinely signed on paper days before anyone enters them here.
 */
internal data class ContractSignature(
    val contractId: ContractId,
    val signerName: String,
    val signerEmail: String?,
    val signedOn: String,
)

/** A project the forms can attach a cost or payment to. */
internal data class ProjectOption(
    val id: ProjectId?,
    val label: String,
)
