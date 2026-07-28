package com.yellowtrack.platform.feature.ledger.presentation.model

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

/**
 * What the quote form collected.
 *
 * One line, for now: a quote with several lines has to be edited rather than typed in one
 * pass, and line editing is its own piece of work. A single-line quote is still an exact
 * figure with a validity date, which is the part that decides bookings.
 */
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

/** A project the forms can attach a cost or payment to. */
internal data class ProjectOption(
    val id: ProjectId?,
    val label: String,
)
