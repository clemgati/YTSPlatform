package com.yellowtrack.platform.feature.ledger.presentation.model

import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.LicenseMedium
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.core.model.project.ProjectId

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

internal data class NewPayment(
    val invoiceId: InvoiceId,
    val amount: String,
    val paidOn: String,
    val method: PaymentMethod,
    val reference: String?,
)

internal data class NewQuote(
    val number: String,
    val projectId: ProjectId,
    val description: String,
    val amount: String,
    val taxRate: String,
    /** Blank means no expiry is set, which the form warns about rather than forbids. */
    val validUntil: String,
    val terms: String?,
)

internal data class NewInvoice(
    val number: String,
    val projectId: ProjectId,
    val kind: InvoiceKind,
    val description: String,
    val amount: String,
    val taxRate: String,
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
