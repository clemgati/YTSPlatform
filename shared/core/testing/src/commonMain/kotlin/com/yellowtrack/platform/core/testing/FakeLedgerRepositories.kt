package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.common.money.sum
import com.yellowtrack.platform.core.data.CodbRepository
import com.yellowtrack.platform.core.data.ContractRepository
import com.yellowtrack.platform.core.data.ExpenseRepository
import com.yellowtrack.platform.core.data.InvoiceRepository
import com.yellowtrack.platform.core.data.QuoteRepository
import com.yellowtrack.platform.core.data.ServiceTemplateRepository
import com.yellowtrack.platform.core.model.codb.CodbBreakdown
import com.yellowtrack.platform.core.model.codb.CodbProfile
import com.yellowtrack.platform.core.model.codb.CostOfDoingBusiness
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.expense.MileageId
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * In-memory ledger repositories for feature tests.
 *
 * Payments are held beside invoices and attached on read, exactly as the SQLDelight
 * implementation does, so a test that records a payment sees the same derived balance and
 * overdue state the application would.
 */
class FakeInvoiceRepository(
    initial: List<Invoice> = emptyList(),
) : InvoiceRepository {
    private val invoices = MutableStateFlow(initial.map { it.copy(payments = emptyList()) })
    private val payments = MutableStateFlow(initial.flatMap(Invoice::payments))

    var failure: Throwable? = null

    private val joined: Flow<List<Invoice>>
        get() =
            combine(invoices, payments) { rows, allPayments ->
                failure?.let { throw it }
                val byInvoice = allPayments.groupBy(Payment::invoiceId)
                rows.map { it.copy(payments = byInvoice[it.id].orEmpty()) }
            }

    override fun observeInvoices(): Flow<List<Invoice>> = joined

    override fun observeInvoice(invoiceId: InvoiceId): Flow<Invoice?> =
        joined.map { rows -> rows.firstOrNull { it.id == invoiceId } }

    override fun observeInvoicesForProject(projectId: ProjectId): Flow<List<Invoice>> =
        joined.map { rows -> rows.filter { it.projectId == projectId } }

    override suspend fun getInvoice(invoiceId: InvoiceId): Invoice? =
        invoices.value
            .firstOrNull { it.id == invoiceId }
            ?.let { invoice -> invoice.copy(payments = payments.value.filter { it.invoiceId == invoice.id }) }

    override suspend fun saveInvoice(invoice: Invoice) {
        invoices.value = invoices.value.filterNot { it.id == invoice.id } + invoice.copy(payments = emptyList())
        invoice.payments.forEach { recordPayment(it) }
    }

    override suspend fun deleteInvoice(invoiceId: InvoiceId) {
        invoices.value = invoices.value.filterNot { it.id == invoiceId }
    }

    override suspend fun recordPayment(payment: Payment) {
        payments.value = payments.value.filterNot { it.id == payment.id } + payment
    }

    override suspend fun deletePayment(paymentId: PaymentId) {
        payments.value = payments.value.filterNot { it.id == paymentId }
    }
}

class FakeQuoteRepository(
    initial: List<Quote> = emptyList(),
) : QuoteRepository {
    private val state = MutableStateFlow(initial)

    var failure: Throwable? = null

    override fun observeQuotes(): Flow<List<Quote>> = state.map { quotes -> failure?.let { throw it } ?: quotes }

    override fun observeQuote(quoteId: QuoteId): Flow<Quote?> =
        state.map { quotes -> quotes.firstOrNull { it.id == quoteId } }

    override fun observeQuotesForProject(projectId: ProjectId): Flow<List<Quote>> =
        state.map { quotes -> quotes.filter { it.projectId == projectId } }

    override fun observeAwaitingDecision(): Flow<List<Quote>> =
        state.map { quotes ->
            quotes
                .filter { it.status.isAwaitingDecision }
                .sortedBy { it.issuedAt ?: it.audit.createdAt }
        }

    override suspend fun getQuote(quoteId: QuoteId): Quote? = state.value.firstOrNull { it.id == quoteId }

    override suspend fun saveQuote(quote: Quote) {
        state.value = state.value.filterNot { it.id == quote.id } + quote
    }

    override suspend fun deleteQuote(quoteId: QuoteId) {
        state.value = state.value.filterNot { it.id == quoteId }
    }
}

class FakeContractRepository(
    initial: List<Contract> = emptyList(),
) : ContractRepository {
    private val state = MutableStateFlow(initial)

    var failure: Throwable? = null

    override fun observeContracts(): Flow<List<Contract>> =
        state.map { contracts -> failure?.let { throw it } ?: contracts }

    override fun observeContract(contractId: ContractId): Flow<Contract?> =
        state.map { contracts -> contracts.firstOrNull { it.id == contractId } }

    override fun observeContractsForProject(projectId: ProjectId): Flow<List<Contract>> =
        state.map { contracts -> contracts.filter { it.projectId == projectId } }

    override fun observeAwaitingSignature(): Flow<List<Contract>> =
        state.map { contracts ->
            contracts
                .filter { it.status == ContractStatus.Sent }
                .sortedBy { it.sentAt ?: it.audit.createdAt }
        }

    override suspend fun getContract(contractId: ContractId): Contract? =
        state.value.firstOrNull { it.id == contractId }

    override suspend fun saveContract(contract: Contract) {
        state.value = state.value.filterNot { it.id == contract.id } + contract
    }

    override suspend fun deleteContract(contractId: ContractId) {
        state.value = state.value.filterNot { it.id == contractId }
    }
}

class FakeExpenseRepository(
    initial: List<Expense> = emptyList(),
    initialMileage: List<Mileage> = emptyList(),
) : ExpenseRepository {
    private val expenses = MutableStateFlow(initial)
    private val mileage = MutableStateFlow(initialMileage)

    var failure: Throwable? = null

    override fun observeExpenses(): Flow<List<Expense>> = expenses.map { rows -> failure?.let { throw it } ?: rows }

    override fun observeExpensesForProject(projectId: ProjectId): Flow<List<Expense>> =
        expenses.map { rows -> rows.filter { it.projectId == projectId } }

    override fun observeOverheadBetween(
        fromInclusive: LocalDate,
        toExclusive: LocalDate,
    ): Flow<List<Expense>> =
        expenses.map { rows ->
            rows.filter { it.isOverhead && it.incurredOn >= fromInclusive && it.incurredOn < toExclusive }
        }

    override suspend fun getExpense(expenseId: ExpenseId): Expense? = expenses.value.firstOrNull { it.id == expenseId }

    override suspend fun saveExpense(expense: Expense) {
        expenses.value = expenses.value.filterNot { it.id == expense.id } + expense
    }

    override suspend fun deleteExpense(expenseId: ExpenseId) {
        expenses.value = expenses.value.filterNot { it.id == expenseId }
    }

    override fun observeMileage(): Flow<List<Mileage>> = mileage

    override fun observeMileageForProject(projectId: ProjectId): Flow<List<Mileage>> =
        mileage.map { rows -> rows.filter { it.projectId == projectId } }

    override suspend fun getMileage(mileageId: MileageId): Mileage? = mileage.value.firstOrNull { it.id == mileageId }

    override suspend fun saveMileage(mileage: Mileage) {
        this.mileage.value = this.mileage.value.filterNot { it.id == mileage.id } + mileage
    }

    override suspend fun deleteMileage(mileageId: MileageId) {
        mileage.value = mileage.value.filterNot { it.id == mileageId }
    }
}

/**
 * Computes the breakdown from the profile and recorded overhead, as the real repository
 * does — so a test that records an overhead cost sees the pricing floor move.
 */
class FakeCodbRepository(
    initial: CodbProfile? = null,
    private val expenses: FakeExpenseRepository = FakeExpenseRepository(),
) : CodbRepository {
    private val state = MutableStateFlow(initial)

    override fun observeProfile(): Flow<CodbProfile?> = state

    override fun observeBreakdown(year: Int): Flow<CodbBreakdown?> =
        combine(
            state,
            expenses.observeOverheadBetween(LocalDate(year, 1, 1), LocalDate(year + 1, 1, 1)),
        ) { profile, overhead ->
            profile?.let {
                CostOfDoingBusiness.calculate(
                    profile = it,
                    overheadFromExpenses =
                        overhead
                            .filter { expense -> expense.amount.currency == it.currency }
                            .map(Expense::amount)
                            .sum(it.currency),
                )
            }
        }

    override suspend fun getProfile(): CodbProfile? = state.value

    override suspend fun saveProfile(profile: CodbProfile) {
        state.value = profile
    }

    /** The overhead this breakdown is computed from, for a test that seeds both. */
    val overheadSource: FakeExpenseRepository get() = expenses
}

class FakeServiceTemplateRepository(
    initial: List<ServiceTemplate> = emptyList(),
) : ServiceTemplateRepository {
    private val state = MutableStateFlow(initial)

    override fun observeTemplates(): Flow<List<ServiceTemplate>> = state

    override suspend fun getTemplate(id: ServiceTemplateId): ServiceTemplate? = state.value.firstOrNull { it.id == id }

    override suspend fun saveTemplate(template: ServiceTemplate) {
        state.value = state.value.filterNot { it.id == template.id } + template
    }

    override suspend fun deleteTemplate(id: ServiceTemplateId) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun seedDefaultsIfEmpty() = Unit
}
