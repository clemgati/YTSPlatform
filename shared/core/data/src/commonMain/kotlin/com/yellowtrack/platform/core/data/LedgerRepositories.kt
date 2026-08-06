package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.codb.CodbBreakdown
import com.yellowtrack.platform.core.model.codb.CodbProfile
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.expense.MileageId
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface LeadRepository {
    fun observeLeads(): Flow<List<Lead>>

    fun observeLead(leadId: LeadId): Flow<Lead?>

    /** Enquiries not yet won or lost. */
    fun observeOpenLeads(): Flow<List<Lead>>

    /**
     * Enquiries never replied to, oldest first.
     *
     * The most actionable list in the application: response time is the strongest
     * predictor of whether an enquiry books.
     */
    fun observeAwaitingResponse(): Flow<List<Lead>>

    suspend fun getLead(leadId: LeadId): Lead?

    suspend fun saveLead(lead: Lead)

    suspend fun deleteLead(leadId: LeadId)
}

interface InvoiceRepository {
    /** Invoices with their payments attached, so every derived figure agrees. */
    fun observeInvoices(): Flow<List<Invoice>>

    fun observeInvoice(invoiceId: InvoiceId): Flow<Invoice?>

    fun observeInvoicesForProject(projectId: ProjectId): Flow<List<Invoice>>

    suspend fun getInvoice(invoiceId: InvoiceId): Invoice?

    suspend fun saveInvoice(invoice: Invoice)

    suspend fun deleteInvoice(invoiceId: InvoiceId)

    /**
     * Records that this invoice was emailed to a client, and where it went.
     *
     * Not `saveInvoice` with two fields changed. Emailing does not edit the document — ADR
     * 0011 decision 5 — and routing it through the ordinary save would make a send look like
     * a correction to every device that received it.
     */
    suspend fun recordInvoiceEmailed(
        invoiceId: InvoiceId,
        to: String,
    )

    suspend fun recordPayment(payment: Payment)

    suspend fun deletePayment(paymentId: PaymentId)
}

interface QuoteRepository {
    fun observeQuotes(): Flow<List<Quote>>

    fun observeQuote(quoteId: QuoteId): Flow<Quote?>

    fun observeQuotesForProject(projectId: ProjectId): Flow<List<Quote>>

    /**
     * Quotes sent and not yet answered, oldest first.
     *
     * Expiry is deliberately not filtered here: a lapsed quote is still the studio's to
     * chase, and dropping it from the list is how a booking is lost silently. Callers
     * decide what to do with [Quote.effectiveStatus].
     */
    fun observeAwaitingDecision(): Flow<List<Quote>>

    suspend fun getQuote(quoteId: QuoteId): Quote?

    suspend fun saveQuote(quote: Quote)

    /** See [InvoiceRepository.recordInvoiceEmailed]. */
    suspend fun recordQuoteEmailed(
        quoteId: QuoteId,
        to: String,
    )

    suspend fun deleteQuote(quoteId: QuoteId)
}

interface ContractRepository {
    fun observeContracts(): Flow<List<Contract>>

    fun observeContract(contractId: ContractId): Flow<Contract?>

    fun observeContractsForProject(projectId: ProjectId): Flow<List<Contract>>

    /** Sent and unsigned. Until one of these is signed, no date is actually held. */
    fun observeAwaitingSignature(): Flow<List<Contract>>

    suspend fun getContract(contractId: ContractId): Contract?

    suspend fun saveContract(contract: Contract)

    suspend fun deleteContract(contractId: ContractId)
}

interface ExpenseRepository {
    fun observeExpenses(): Flow<List<Expense>>

    fun observeExpensesForProject(projectId: ProjectId): Flow<List<Expense>>

    /**
     * Overhead — expenses attached to no project — within a period.
     *
     * This is the input to cost of doing business, and the reason the project link is
     * nullable rather than required.
     */
    fun observeOverheadBetween(
        fromInclusive: LocalDate,
        toExclusive: LocalDate,
    ): Flow<List<Expense>>

    /**
     * One cost, for correcting it.
     *
     * A correction has to keep the row it is correcting: its id, so it is the same cost
     * everywhere, its `createdAt`, because when the money left is a fact about the cost
     * rather than about when the mistake was spotted, and its `version`, which is what
     * reconciliation compares.
     */
    suspend fun getExpense(expenseId: ExpenseId): Expense?

    suspend fun saveExpense(expense: Expense)

    suspend fun deleteExpense(expenseId: ExpenseId)

    fun observeMileage(): Flow<List<Mileage>>

    fun observeMileageForProject(projectId: ProjectId): Flow<List<Mileage>>

    /** One journey, for correcting it — see the note on [ExpenseRepository.getExpense]. */
    suspend fun getMileage(mileageId: MileageId): Mileage?

    suspend fun saveMileage(mileage: Mileage)

    suspend fun deleteMileage(mileageId: MileageId)
}

interface CodbRepository {
    fun observeProfile(): Flow<CodbProfile?>

    /**
     * The profile combined with recorded overhead, ready to price against.
     *
     * Emits null until a studio has stated its salary target and billable days — the two
     * figures the calculation cannot guess.
     */
    fun observeBreakdown(year: Int): Flow<CodbBreakdown?>

    suspend fun getProfile(): CodbProfile?

    suspend fun saveProfile(profile: CodbProfile)
}

/**
 * What a booking actually earned, after the costs attributed to it.
 *
 * Revenue is taken from invoices rather than the project's contract value, because an
 * agreed figure and an invoiced one diverge the moment anything is added or discounted.
 */
data class ProjectMargin(
    val projectId: ProjectId,
    val invoiced: Money,
    val collected: Money,
    val outstanding: Money,
    val directCosts: Money,
    val mileageCosts: Money,
) {
    val totalCosts: Money get() = directCosts + mileageCosts

    /** Against invoiced revenue, not collected — an unpaid invoice is still earned. */
    val margin: Money get() = invoiced - totalCosts

    val isLoss: Boolean get() = margin.isNegative

    /** Margin as a percentage of revenue, in basis points. Null when nothing was invoiced. */
    val marginBasisPoints: Int?
        get() =
            if (invoiced.isZero) {
                null
            } else {
                ((margin.minorUnits * 10_000) / invoiced.minorUnits).toInt()
            }
}
