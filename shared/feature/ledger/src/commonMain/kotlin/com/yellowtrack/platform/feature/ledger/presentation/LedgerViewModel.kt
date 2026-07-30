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
import com.yellowtrack.platform.core.data.PostProductionRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.QuoteRepository
import com.yellowtrack.platform.core.data.ServiceTemplateRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.export.Document
import com.yellowtrack.platform.core.export.DocumentFormat
import com.yellowtrack.platform.core.export.DocumentSink
import com.yellowtrack.platform.core.export.Sheet
import com.yellowtrack.platform.core.export.buildInvoice
import com.yellowtrack.platform.core.export.buildQuote
import com.yellowtrack.platform.core.export.slugify
import com.yellowtrack.platform.core.export.toHtml
import com.yellowtrack.platform.core.export.toPlainText
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
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.model.quote.accepted
import com.yellowtrack.platform.core.model.quote.declined
import com.yellowtrack.platform.core.model.quote.toInvoice
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.ledger.presentation.mapper.INVOICE_PREFIX
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildExpenseSummary
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildMoneyOwed
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildPricing
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildProposals
import com.yellowtrack.platform.feature.ledger.presentation.mapper.measuredPostProductionFactor
import com.yellowtrack.platform.feature.ledger.presentation.mapper.nextNumber
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractSignature
import com.yellowtrack.platform.feature.ledger.presentation.model.NewContract
import com.yellowtrack.platform.feature.ledger.presentation.model.NewExpense
import com.yellowtrack.platform.feature.ledger.presentation.model.NewInvoice
import com.yellowtrack.platform.feature.ledger.presentation.model.NewPayment
import com.yellowtrack.platform.feature.ledger.presentation.model.NewQuote
import com.yellowtrack.platform.feature.ledger.presentation.model.NewUsageLicense
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import com.yellowtrack.platform.feature.ledger.presentation.model.toLineItems
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
    private val sessionRepository: SessionRepository,
    private val postProductionRepository: PostProductionRepository,
    private val clientRepository: ClientRepository,
    private val studioProfileRepository: StudioProfileRepository,
    private val documentSink: DocumentSink,
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

    /**
     * What the studio has actually done, as opposed to what it has billed.
     *
     * Kept apart from the money so the pricing floor can be told how long work really
     * takes rather than assuming it.
     */
    private data class Work(
        val sessions: List<Session>,
        val completedTasks: List<PostProductionTask>,
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
                    combine(
                        sessionRepository.observeSessions(),
                        postProductionRepository.observeCompletedTasks(),
                        ::Work,
                    ),
                ) { books, costs, proposals, work ->
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
                                    pricing =
                                        books.breakdown?.let {
                                            buildPricing(
                                                it,
                                                books.templates,
                                                measuredPostProductionFactor(
                                                    completedTasks = work.completedTasks,
                                                    shootHours = work.sessions.shootHours(),
                                                ),
                                            )
                                        },
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

    /**
     * Why nothing can be sent out, or null when it can.
     *
     * Checked once for both copying and saving. A studio with no name on file can still
     * see its invoices; it simply cannot send one, and being told why beats a button that
     * does nothing.
     */
    suspend fun documentBlocker(): String? =
        if (studioProfileRepository.getProfile()?.canIssueDocuments == true) {
            null
        } else {
            "Add your studio's name in Settings before sending anything out."
        }

    /** The invoice as the client would receive it, or null if it or the studio has gone. */
    suspend fun invoiceSheet(invoiceId: InvoiceId): Sheet? {
        val invoice = invoiceRepository.getInvoice(invoiceId) ?: return null
        val studio = studioProfileRepository.getProfile()?.takeIf { it.canIssueDocuments } ?: return null
        val project = projectRepository.observeProjects().first().firstOrNull { it.id == invoice.projectId }

        return buildInvoice(
            invoice = invoice,
            project = project,
            client =
                project?.let { found ->
                    clientRepository.observeClients().first().firstOrNull {
                        it.id ==
                            found.clientId
                    }
                },
            studio = studio,
            now = clock.now(),
            zone = timeZone,
        )
    }

    suspend fun quoteSheet(quoteId: QuoteId): Sheet? {
        val quote = quoteRepository.getQuote(quoteId) ?: return null
        val studio = studioProfileRepository.getProfile()?.takeIf { it.canIssueDocuments } ?: return null
        val project = projectRepository.observeProjects().first().firstOrNull { it.id == quote.projectId }

        return buildQuote(
            quote = quote,
            project = project,
            client =
                project?.let { found ->
                    clientRepository.observeClients().first().firstOrNull {
                        it.id ==
                            found.clientId
                    }
                },
            studio = studio,
            now = clock.now(),
            zone = timeZone,
        )
    }

    /**
     * Writes a document out as a web page.
     *
     * HTML rather than PDF for the same reason the call sheet is: it opens anywhere, and
     * prints to PDF from the browser without a rendering library on four platforms.
     */
    fun saveDocument(
        sheet: suspend () -> Sheet?,
        onResult: (String) -> Unit,
    ) {
        viewModelScope.launch {
            documentBlocker()?.let {
                onResult(it)
                return@launch
            }

            val document =
                sheet() ?: run {
                    onResult("That document could not be read.")
                    return@launch
                }

            val saved =
                documentSink.save(
                    Document(
                        baseName = slugify(document.title),
                        format = DocumentFormat.Html,
                        content = document.toHtml(),
                    ),
                )

            onResult("Saved to ${saved.location}")
        }
    }

    /** The plain-text rendering, for pasting into an email. */
    suspend fun documentText(sheet: suspend () -> Sheet?): String? = sheet()?.toPlainText()

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
            val lines = quote.lines.toLineItems(currency) ?: return@launch
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
                    lines = lines,
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
            val lines = invoice.lines.toLineItems(currency) ?: return@launch
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
                    lines = lines,
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
     * Issues a draft invoice.
     *
     * This is the step that turns work already agreed into money owed. The issue date is
     * stamped now rather than backdated to when the draft was raised, because the due date
     * a client is held to runs from the demand they actually received.
     */
    fun sendInvoice(invoiceId: InvoiceId) {
        viewModelScope.launch {
            val invoice = invoiceRepository.getInvoice(invoiceId) ?: return@launch
            if (invoice.status != InvoiceStatus.Draft) return@launch
            val now = clock.now()

            invoiceRepository.saveInvoice(
                invoice.copy(
                    status = InvoiceStatus.Sent,
                    issuedAt = now,
                    audit = invoice.audit.touched(now),
                ),
            )
        }
    }

    /**
     * Cancels an invoice while keeping it in the books.
     *
     * Voiding rather than deleting is what keeps the numbering honest: the row stays, so
     * its number is never handed to a second document, and a client holding INV-008 can
     * always be shown what INV-008 was. An invoice with payments against it is refused —
     * cancelling it would take money the studio has actually received out of its books,
     * and the remedy for that is a refund, recorded.
     */
    fun voidInvoice(invoiceId: InvoiceId) {
        viewModelScope.launch {
            val invoice = invoiceRepository.getInvoice(invoiceId) ?: return@launch
            if (invoice.status == InvoiceStatus.Void || invoice.payments.isNotEmpty()) return@launch
            val now = clock.now()

            invoiceRepository.saveInvoice(
                invoice.copy(
                    status = InvoiceStatus.Void,
                    audit = invoice.audit.touched(now),
                ),
            )
        }
    }

    /**
     * Discards a draft invoice outright.
     *
     * Only a draft: it has never been sent, so nobody holds a copy, nothing has been
     * demanded, and its number may safely be handed to the next document. Anything that
     * has left the studio is voided instead, which is why this refuses everything else.
     */
    fun deleteInvoice(invoiceId: InvoiceId) {
        viewModelScope.launch {
            val invoice = invoiceRepository.getInvoice(invoiceId) ?: return@launch
            if (invoice.status != InvoiceStatus.Draft) return@launch

            invoiceRepository.deleteInvoice(invoiceId)
        }
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

/**
 * Hours actually spent with a camera, across work that has happened.
 *
 * Only completed sessions count. A day still in the diary has consumed nothing yet, and
 * including it would make the studio look faster at post-production than it is.
 */
private fun List<Session>.shootHours(): Double =
    filter { it.status == SessionStatus.Completed }
        .sumOf { it.duration.inWholeMinutes / 60.0 }
