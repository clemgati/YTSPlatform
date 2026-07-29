package com.yellowtrack.platform.feature.ledger.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.basisPointsAsPercentage
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.money.parsePercentageToBasisPoints
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.CodbRepository
import com.yellowtrack.platform.core.data.ContractRepository
import com.yellowtrack.platform.core.data.ExpenseRepository
import com.yellowtrack.platform.core.data.InvoiceRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.QuoteRepository
import com.yellowtrack.platform.core.data.ServiceTemplateRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.codb.CodbBreakdown
import com.yellowtrack.platform.core.model.codb.CodbProfile
import com.yellowtrack.platform.core.model.codb.CodbProfileId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.contract.LicenseMedium
import com.yellowtrack.platform.core.model.contract.UsageLicense
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.model.quote.accepted
import com.yellowtrack.platform.core.model.quote.declined
import com.yellowtrack.platform.core.model.quote.toInvoice
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.ledger.presentation.mapper.INVOICE_PREFIX
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildExpenseSummary
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildMoneyOwed
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildPricing
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildProposals
import com.yellowtrack.platform.feature.ledger.presentation.mapper.nextNumber
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractSignature
import com.yellowtrack.platform.feature.ledger.presentation.model.NewContract
import com.yellowtrack.platform.feature.ledger.presentation.model.NewExpense
import com.yellowtrack.platform.feature.ledger.presentation.model.NewInvoice
import com.yellowtrack.platform.feature.ledger.presentation.model.NewPayment
import com.yellowtrack.platform.feature.ledger.presentation.model.NewQuote
import com.yellowtrack.platform.feature.ledger.presentation.model.NewUsageLicense
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

internal class LedgerViewModel(
    private val invoiceRepository: InvoiceRepository,
    private val quoteRepository: QuoteRepository,
    private val contractRepository: ContractRepository,
    private val expenseRepository: ExpenseRepository,
    private val codbRepository: CodbRepository,
    private val serviceTemplateRepository: ServiceTemplateRepository,
    private val projectRepository: ProjectRepository,
    private val clientRepository: ClientRepository,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val currency: CurrencyCode = CurrencyCode.USD,
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

    /**
     * The ledger reads from eight sources at once, grouped into three so that each
     * `combine` stays within its typed arity and, more usefully, so the destructuring at
     * the join reads as three named things rather than nested pairs.
     */
    private data class Books(
        val invoices: List<Invoice>,
        val projects: List<Project>,
        val clients: List<Client>,
        val breakdown: CodbBreakdown?,
        val templates: List<ServiceTemplate>,
    )

    private data class Costs(
        val expenses: List<Expense>,
        val mileage: List<Mileage>,
        val profile: CodbProfile?,
    )

    private data class Proposals(
        val quotes: List<Quote>,
        val contracts: List<Contract>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LedgerUiState> =
        retryTrigger
            .flatMapLatest {
                val now = clock.now()
                val year = now.toLocalDateTime(timeZone).year

                combine(
                    combine(
                        invoiceRepository.observeInvoices(),
                        projectRepository.observeProjects(),
                        clientRepository.observeClients(),
                        codbRepository.observeBreakdown(year),
                        serviceTemplateRepository.observeTemplates(),
                        ::Books,
                    ),
                    combine(
                        expenseRepository.observeExpenses(),
                        expenseRepository.observeMileage(),
                        codbRepository.observeProfile(),
                        ::Costs,
                    ),
                    combine(
                        quoteRepository.observeQuotes(),
                        contractRepository.observeContracts(),
                        ::Proposals,
                    ),
                ) { books, costs, proposals ->
                    LedgerUiState(
                        content =
                            UiState.Success(
                                LedgerContent(
                                    moneyOwed =
                                        buildMoneyOwed(
                                            books.invoices,
                                            books.projects,
                                            books.clients,
                                            now,
                                            currency,
                                        ),
                                    proposals =
                                        buildProposals(
                                            proposals.quotes,
                                            proposals.contracts,
                                            books.invoices,
                                            books.projects,
                                            books.clients,
                                            now,
                                            currency,
                                        ),
                                    pricing = books.breakdown?.let { buildPricing(it, books.templates) },
                                    expenses =
                                        buildExpenseSummary(costs.expenses, costs.mileage, year, currency),
                                    projects =
                                        books.projects.map { project ->
                                            ProjectOption(
                                                id = project.id,
                                                label =
                                                    books.clients
                                                        .firstOrNull { it.id == project.clientId }
                                                        ?.let { "${project.name} — ${it.displayName}" }
                                                        ?: project.name,
                                            )
                                        },
                                    today = now.toLocalDateTime(timeZone).date,
                                    currency = currency,
                                    pricingBasis =
                                        PricingBasisFields(
                                            salary =
                                                costs.profile
                                                    ?.targetAnnualSalary
                                                    ?.toPlainString()
                                                    .orEmpty(),
                                            billableDays =
                                                costs.profile
                                                    ?.billableDaysPerYear
                                                    ?.toString()
                                                    .orEmpty(),
                                            taxRate =
                                                costs.profile
                                                    ?.taxRateBasisPoints
                                                    ?.basisPointsAsPercentage()
                                                    .orEmpty(),
                                            currency = currency,
                                        ),
                                ),
                            ),
                    )
                }.catch { throwable ->
                    emit(
                        LedgerUiState(
                            content = UiState.Error(throwable.message ?: "Unable to load the ledger."),
                        ),
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = LedgerUiState(content = UiState.Loading),
            )

    fun retry() {
        retryTrigger.value += 1
    }

    /**
     * Saves the pricing basis, creating the profile on first use.
     *
     * Parsing happens here rather than in the form so the rules live with the domain:
     * the screen decides what to enable, this decides what is valid.
     */
    fun savePricingBasis(
        salaryText: String,
        billableDaysText: String,
        taxRateText: String,
    ) {
        viewModelScope.launch {
            val salary = parseMoney(salaryText, currency) ?: return@launch
            val billableDays = billableDaysText.toIntOrNull()?.takeIf { it in 1..366 } ?: return@launch
            val taxBasisPoints =
                if (taxRateText.isBlank()) {
                    0
                } else {
                    parsePercentageToBasisPoints(taxRateText)?.takeIf { it < 10_000 } ?: return@launch
                }

            val existing = codbRepository.getProfile()

            codbRepository.saveProfile(
                existing?.copy(
                    currency = currency,
                    targetAnnualSalary = salary,
                    billableDaysPerYear = billableDays,
                    taxRateBasisPoints = taxBasisPoints,
                    audit = existing.audit.touched(clock.now()),
                ) ?: CodbProfile(
                    id = CodbProfileId.new(),
                    studioId = studioContext.studioId,
                    currency = currency,
                    targetAnnualSalary = salary,
                    billableDaysPerYear = billableDays,
                    taxRateBasisPoints = taxBasisPoints,
                    audit = AuditMetadata.createdAt(clock.now()),
                ),
            )
        }
    }

    fun addExpense(expense: NewExpense) {
        viewModelScope.launch {
            val amount = parseMoney(expense.amount, currency)?.takeIf { it.isPositive } ?: return@launch
            val incurredOn = runCatching { LocalDate.parse(expense.incurredOn) }.getOrNull() ?: return@launch
            val now = clock.now()

            expenseRepository.saveExpense(
                Expense(
                    id = ExpenseId.new(),
                    studioId = studioContext.studioId,
                    category = expense.category,
                    description = expense.description,
                    amount = amount,
                    incurredOn = incurredOn,
                    projectId = expense.projectId,
                    vendor = expense.vendor,
                    isTaxDeductible = expense.isTaxDeductible,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Raises a quote and sends it in one step.
     *
     * There is no draft state in the form because a quote nobody has seen is not yet a
     * proposal, and a draft that is never sent is the commonest way an enquiry goes cold.
     */
    fun addQuote(quote: NewQuote) {
        viewModelScope.launch {
            val line = lineItemOf(quote.description, quote.amount, quote.taxRate) ?: return@launch
            val now = clock.now()
            val validUntil =
                if (quote.validUntil.isBlank()) {
                    null
                } else {
                    runCatching { LocalDate.parse(quote.validUntil) }.getOrNull()?.atStartOfDayIn(timeZone)
                        ?: return@launch
                }

            quoteRepository.saveQuote(
                Quote(
                    id = QuoteId.new(),
                    studioId = studioContext.studioId,
                    projectId = quote.projectId,
                    number = quote.number.trim(),
                    status = QuoteStatus.Sent,
                    currency = currency,
                    lines = listOf(line),
                    issuedAt = now,
                    validUntil = validUntil,
                    terms = quote.terms,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Accepts a quote and raises the invoice that collects it.
     *
     * The invoice carries the quote's lines untouched — see
     * [com.yellowtrack.platform.core.model.quote.toInvoice] — and is raised as a draft, so
     * accepting never puts an unreviewed figure into money owed.
     */
    fun acceptQuote(quoteId: QuoteId) {
        viewModelScope.launch {
            val quote = quoteRepository.getQuote(quoteId) ?: return@launch
            val now = clock.now()

            quoteRepository.saveQuote(quote.accepted(now))

            val existingNumbers = invoiceRepository.observeInvoices().first().map(Invoice::number)

            invoiceRepository.saveInvoice(
                quote.toInvoice(
                    number = nextNumber(INVOICE_PREFIX, existingNumbers),
                    now = now,
                    dueAt = now + DEFAULT_PAYMENT_TERMS,
                ),
            )
        }
    }

    fun declineQuote(quoteId: QuoteId) {
        viewModelScope.launch {
            val quote = quoteRepository.getQuote(quoteId) ?: return@launch
            quoteRepository.saveQuote(quote.declined(clock.now()))
        }
    }

    /**
     * Draws up a contract, and sends it if asked.
     *
     * A contract may be saved unsent, unlike a quote: the terms are usually settled before
     * anyone is willing to put them in front of a client, and an unsent contract is
     * visible on the Ledger as the studio's own outstanding step rather than lost.
     */
    fun addContract(contract: NewContract) {
        viewModelScope.launch {
            val retainer =
                when {
                    contract.retainerAmount.isBlank() -> null
                    else -> parseMoney(contract.retainerAmount, currency)?.takeIf { it.isPositive } ?: return@launch
                }

            val turnaroundDays = optionalCount(contract.turnaroundDays) ?: return@launch
            val revisionRounds = optionalCount(contract.revisionRounds) ?: return@launch
            val license = contract.license?.let { form -> usageLicenseOf(form) ?: return@launch }
            val now = clock.now()

            contractRepository.saveContract(
                Contract(
                    id = ContractId.new(),
                    studioId = studioContext.studioId,
                    projectId = contract.projectId,
                    title = contract.title.trim(),
                    status = if (contract.sendNow) ContractStatus.Sent else ContractStatus.Draft,
                    sentAt = now.takeIf { contract.sendNow },
                    retainerAmount = retainer,
                    isRetainerRefundable = contract.isRetainerRefundable,
                    turnaroundDays = turnaroundDays.value,
                    revisionRounds = revisionRounds.value,
                    cancellationTerms = contract.cancellationTerms,
                    rescheduleTerms = contract.rescheduleTerms,
                    weatherClause = contract.weatherClause,
                    usageLicense = license,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Builds the licence, or null if the stated duration does not parse.
     *
     * An unreadable duration rejects the contract rather than quietly becoming perpetual.
     * Defaulting the other way would grant, for free, the one term that forecloses every
     * future fee from the same work.
     */
    private fun usageLicenseOf(form: NewUsageLicense): UsageLicense? {
        if (form.media.isEmpty() || form.territory.isBlank()) return null

        val durationMonths =
            when {
                form.durationMonths.isBlank() -> null
                else -> form.durationMonths.toIntOrNull()?.takeIf { it > 0 } ?: return null
            }

        val startsOn =
            when {
                form.startsOn.isBlank() -> null
                else -> runCatching { LocalDate.parse(form.startsOn) }.getOrNull() ?: return null
            }

        return UsageLicense(
            media = form.media.sortedBy(LicenseMedium::ordinal),
            territory = form.territory.trim(),
            durationMonths = durationMonths,
            isExclusive = form.isExclusive,
            startsOn = startsOn,
        )
    }

    /**
     * Reads an optional whole count, distinguishing "not stated" from "not a number".
     *
     * Blank means the contract is silent on the term, which is legitimate. Text that is
     * not a positive number is a typo, and saving the contract without it would drop a
     * term the studio believed it had agreed.
     */
    private fun optionalCount(text: String): OptionalCount? =
        when {
            text.isBlank() -> OptionalCount(null)
            else -> text.toIntOrNull()?.takeIf { it > 0 }?.let(::OptionalCount)
        }

    /** Wraps a nullable count so an absent one is distinguishable from a rejected one. */
    private data class OptionalCount(
        val value: Int?,
    )

    /** Puts a drawn-up contract in front of the client, starting the clock on a reply. */
    fun sendContract(contractId: ContractId) {
        viewModelScope.launch {
            val contract = contractRepository.getContract(contractId) ?: return@launch
            if (contract.status != ContractStatus.Draft) return@launch
            val now = clock.now()

            contractRepository.saveContract(
                contract.copy(
                    status = ContractStatus.Sent,
                    sentAt = now,
                    audit = contract.audit.touched(now),
                ),
            )
        }
    }

    /**
     * Records a signature against a contract.
     *
     * The date comes from the form rather than the clock because contracts are signed on
     * paper and entered later, and the date a client was bound is the date that decides
     * whether a cancellation falls inside the notice period.
     */
    fun signContract(signature: ContractSignature) {
        viewModelScope.launch {
            val contract = contractRepository.getContract(signature.contractId) ?: return@launch
            if (contract.isSigned) return@launch

            val signerName = signature.signerName.trim().ifBlank { return@launch }
            val signedOn = runCatching { LocalDate.parse(signature.signedOn) }.getOrNull() ?: return@launch
            val now = clock.now()

            contractRepository.saveContract(
                contract.copy(
                    status = ContractStatus.Signed,
                    signedAt = signedOn.atStartOfDayIn(timeZone),
                    signerName = signerName,
                    signerEmail = signature.signerEmail?.trim()?.ifBlank { null },
                    audit = contract.audit.touched(now),
                ),
            )
        }
    }

    fun addInvoice(invoice: NewInvoice) {
        viewModelScope.launch {
            val line = lineItemOf(invoice.description, invoice.amount, invoice.taxRate) ?: return@launch
            val dueOn = runCatching { LocalDate.parse(invoice.dueOn) }.getOrNull() ?: return@launch
            val now = clock.now()

            invoiceRepository.saveInvoice(
                Invoice(
                    id = InvoiceId.new(),
                    studioId = studioContext.studioId,
                    projectId = invoice.projectId,
                    number = invoice.number.trim(),
                    kind = invoice.kind,
                    status = if (invoice.sendNow) InvoiceStatus.Sent else InvoiceStatus.Draft,
                    currency = currency,
                    lines = listOf(line),
                    // Stamped only when sent: an unissued invoice has no issue date, and
                    // inventing one would make a draft look like a demand already made.
                    issuedAt = now.takeIf { invoice.sendNow },
                    dueAt = dueOn.atStartOfDayIn(timeZone),
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Builds a billable line, or null if the amount does not parse.
     *
     * A blank tax rate is zero rather than a rejection: most portrait and wedding work is
     * quoted tax-inclusive or tax-free, and forcing a `0` into the field to proceed would
     * be friction with no meaning.
     */
    private fun lineItemOf(
        description: String,
        amount: String,
        taxRate: String,
    ): LineItem? {
        val price = parseMoney(amount, currency)?.takeIf { it.isPositive } ?: return null
        val basisPoints =
            if (taxRate.isBlank()) {
                0
            } else {
                parsePercentageToBasisPoints(taxRate)?.takeIf { it >= 0 } ?: return null
            }

        return LineItem(
            description = description.trim(),
            unitPrice = price,
            taxRateBasisPoints = basisPoints,
        )
    }

    fun recordPayment(payment: NewPayment) {
        viewModelScope.launch {
            val amount = parseMoney(payment.amount, currency)?.takeIf { it.isPositive } ?: return@launch
            val paidOn = runCatching { LocalDate.parse(payment.paidOn) }.getOrNull() ?: return@launch
            val now = clock.now()

            invoiceRepository.recordPayment(
                Payment(
                    id = PaymentId.new(),
                    studioId = studioContext.studioId,
                    invoiceId = payment.invoiceId,
                    amount = amount,
                    paidAt = paidOn.atStartOfDayIn(timeZone),
                    method = payment.method,
                    reference = payment.reference,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * How long an invoice raised from an accepted quote gets before it is due.
         *
         * Fourteen days rather than thirty: photography is usually paid before or on
         * delivery, and a longer default quietly funds the client.
         */
        val DEFAULT_PAYMENT_TERMS = 14.days
    }
}
