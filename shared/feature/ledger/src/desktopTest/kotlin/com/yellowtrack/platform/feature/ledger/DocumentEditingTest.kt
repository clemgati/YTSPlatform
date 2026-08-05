package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeCodbRepository
import com.yellowtrack.platform.core.testing.FakeContractRepository
import com.yellowtrack.platform.core.testing.FakeExpenseRepository
import com.yellowtrack.platform.core.testing.FakeInvoiceRepository
import com.yellowtrack.platform.core.testing.FakePostProductionRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeQuoteRepository
import com.yellowtrack.platform.core.testing.FakeServiceTemplateRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.RecordingDocumentSender
import com.yellowtrack.platform.core.testing.RecordingDocumentSink
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
import com.yellowtrack.platform.feature.ledger.presentation.model.NewContract
import com.yellowtrack.platform.feature.ledger.presentation.model.NewInvoice
import com.yellowtrack.platform.feature.ledger.presentation.model.NewLineItem
import com.yellowtrack.platform.feature.ledger.presentation.model.NewQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A document can be corrected until it becomes the record of something that happened.
 *
 * One rule across all three, and the line is the same each time. An invoice sent is a
 * demand made. A quote answered is a decision recorded. A contract signed is an agreement
 * struck. Before that point the document is still being written, and mistyping one figure
 * of three should not mean building the whole thing again.
 *
 * After it, correcting in place would leave the studio's copy and the client's copy
 * disagreeing while carrying the same number — which is the thing document numbering exists
 * to prevent. The remedy there is voiding, declining or superseding, all of which exist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentEditingTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Invoices ----------------------------------------------------------------------------

    @Test
    fun `a draft invoice can be corrected`() =
        runTest {
            val invoices = FakeInvoiceRepository(listOf(invoice(InvoiceStatus.Draft)))
            val viewModel = viewModel(invoices = invoices)

            viewModel.saveInvoice(invoiceForm(amount = "900.00"), existingId = INVOICE)

            val stored = invoices.observeInvoices().first().single()
            assertEquals(INVOICE, stored.id, "a correction corrects the draft rather than raising a second")
            assertEquals(
                90_000L,
                stored.lines
                    .single()
                    .unitPrice.minorUnits,
            )
        }

    @Test
    fun `a sent invoice is not corrected`() =
        runTest {
            val invoices = FakeInvoiceRepository(listOf(invoice(InvoiceStatus.Sent)))
            val viewModel = viewModel(invoices = invoices)

            viewModel.saveInvoice(invoiceForm(amount = "900.00"), existingId = INVOICE)

            assertEquals(
                120_000L,
                invoices
                    .observeInvoices()
                    .first()
                    .single()
                    .lines
                    .single()
                    .unitPrice.minorUnits,
                "somebody holds a copy of this demand; changing the figure under the same " +
                    "number is what voiding exists to avoid",
            )
        }

    @Test
    fun `correcting a draft does not send it`() =
        runTest {
            val invoices = FakeInvoiceRepository(listOf(invoice(InvoiceStatus.Draft)))
            val viewModel = viewModel(invoices = invoices)

            viewModel.saveInvoice(invoiceForm(sendNow = false), existingId = INVOICE)

            assertEquals(
                InvoiceStatus.Draft,
                invoices
                    .observeInvoices()
                    .first()
                    .single()
                    .status,
                "sending is a separate decision and stays one",
            )
        }

    // -- Quotes ------------------------------------------------------------------------------

    @Test
    fun `a quote awaiting a decision can be revised`() =
        runTest {
            val quotes = FakeQuoteRepository(listOf(quote(QuoteStatus.Sent)))
            val viewModel = viewModel(quotes = quotes)

            viewModel.saveQuote(quoteForm(amount = "900.00"), existingId = QUOTE)

            assertEquals(
                90_000L,
                quotes
                    .observeQuotes()
                    .first()
                    .single()
                    .lines
                    .single()
                    .unitPrice.minorUnits,
                "revising a proposal the client has not answered is ordinary practice",
            )
        }

    @Test
    fun `an accepted quote is not revised`() =
        runTest {
            val quotes = FakeQuoteRepository(listOf(quote(QuoteStatus.Accepted)))
            val viewModel = viewModel(quotes = quotes)

            viewModel.saveQuote(quoteForm(amount = "900.00"), existingId = QUOTE)

            assertEquals(
                120_000L,
                quotes
                    .observeQuotes()
                    .first()
                    .single()
                    .lines
                    .single()
                    .unitPrice.minorUnits,
                "an accepted quote is what the invoice collecting it was raised from",
            )
        }

    @Test
    fun `revising a quote keeps the date it was first sent`() =
        runTest {
            val issued = TestAppClock.DEFAULT_NOW
            val quotes = FakeQuoteRepository(listOf(quote(QuoteStatus.Sent).copy(issuedAt = issued)))
            val viewModel = viewModel(quotes = quotes)

            viewModel.saveQuote(quoteForm(amount = "900.00"), existingId = QUOTE)

            assertEquals(
                issued,
                quotes
                    .observeQuotes()
                    .first()
                    .single()
                    .issuedAt,
                "moving it would restart the clock on how long the client has been sitting " +
                    "on the decision",
            )
        }

    // -- Contracts ---------------------------------------------------------------------------

    @Test
    fun `an unsigned contract can be corrected`() =
        runTest {
            val contracts = FakeContractRepository(listOf(contract(ContractStatus.Sent)))
            val viewModel = viewModel(contracts = contracts)

            viewModel.saveContract(contractForm(title = "Wedding coverage, revised"), existingId = CONTRACT)

            assertEquals(
                "Wedding coverage, revised",
                contracts
                    .observeContracts()
                    .first()
                    .single()
                    .title,
            )
        }

    @Test
    fun `a signed contract is not corrected`() =
        runTest {
            val contracts = FakeContractRepository(listOf(contract(ContractStatus.Signed)))
            val viewModel = viewModel(contracts = contracts)

            viewModel.saveContract(contractForm(title = "Something else entirely"), existingId = CONTRACT)

            assertEquals(
                "Wedding coverage",
                contracts
                    .observeContracts()
                    .first()
                    .single()
                    .title,
                "a signature is somebody agreeing to particular words, and changing the words " +
                    "while keeping the agreement is what a contract exists to prevent",
            )
        }

    @Test
    fun `correcting a sent contract does not un-send it`() =
        runTest {
            val contracts = FakeContractRepository(listOf(contract(ContractStatus.Sent)))
            val viewModel = viewModel(contracts = contracts)

            viewModel.saveContract(contractForm(title = "Wedding coverage, revised"), existingId = CONTRACT)

            val stored = contracts.observeContracts().first().single()
            assertEquals(ContractStatus.Sent, stored.status)
            assertTrue(stored.sentAt != null, "the date it went out is not something a correction should lose")
        }

    // -- Fixtures ----------------------------------------------------------------------------

    private fun viewModel(
        invoices: FakeInvoiceRepository = FakeInvoiceRepository(),
        quotes: FakeQuoteRepository = FakeQuoteRepository(),
        contracts: FakeContractRepository = FakeContractRepository(),
    ): LedgerViewModel {
        val expenses = FakeExpenseRepository()

        return LedgerViewModel(
            invoiceRepository = invoices,
            quoteRepository = quotes,
            contractRepository = contracts,
            expenseRepository = expenses,
            codbRepository = FakeCodbRepository(expenses = expenses),
            serviceTemplateRepository = FakeServiceTemplateRepository(),
            projectRepository = FakeProjectRepository(),
            sessionRepository = FakeSessionRepository(),
            postProductionRepository = FakePostProductionRepository(),
            clientRepository = FakeClientRepository(),
            studioProfileRepository = FakeStudioProfileRepository(),
            documentSink = RecordingDocumentSink(),
            documentSender = RecordingDocumentSender(),
            studioContext = LocalStudioContext(),
            clock = TestAppClock(),
            timeZone = TimeZone.UTC,
        )
    }

    private fun line(amount: String) = listOf(NewLineItem(description = "Coverage", unitPrice = amount))

    private fun invoiceForm(
        amount: String = "1200.00",
        sendNow: Boolean = false,
    ) = NewInvoice(
        number = "2026-014",
        projectId = PROJECT,
        kind = InvoiceKind.Balance,
        lines = line(amount),
        dueOn = "2026-09-01",
        sendNow = sendNow,
    )

    private fun quoteForm(amount: String = "1200.00") =
        NewQuote(
            number = "Q-2026-014",
            projectId = PROJECT,
            lines = line(amount),
            validUntil = "",
            terms = null,
        )

    private fun contractForm(title: String) =
        NewContract(
            projectId = PROJECT,
            title = title,
            retainerAmount = "",
            isRetainerRefundable = false,
            turnaroundDays = "",
            revisionRounds = "",
            cancellationTerms = null,
            rescheduleTerms = null,
            weatherClause = null,
            license = null,
            sendNow = false,
        )

    private fun storedLine() =
        listOf(
            LineItem(
                description = "Coverage",
                unitPrice = Money(minorUnits = 120_000, currency = CurrencyCode.USD),
            ),
        )

    private fun invoice(status: InvoiceStatus) =
        Invoice(
            id = INVOICE,
            studioId = STUDIO,
            projectId = PROJECT,
            number = "2026-014",
            kind = InvoiceKind.Balance,
            status = status,
            currency = CurrencyCode.USD,
            lines = storedLine(),
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private fun quote(status: QuoteStatus) =
        Quote(
            id = QUOTE,
            studioId = STUDIO,
            projectId = PROJECT,
            number = "Q-2026-014",
            status = status,
            currency = CurrencyCode.USD,
            lines = storedLine(),
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private fun contract(status: ContractStatus) =
        Contract(
            id = CONTRACT,
            studioId = STUDIO,
            projectId = PROJECT,
            title = "Wedding coverage",
            status = status,
            sentAt = TestAppClock.DEFAULT_NOW,
            signedAt = TestAppClock.DEFAULT_NOW.takeIf { status == ContractStatus.Signed },
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private companion object {
        val STUDIO = StudioId("studio-1")
        val PROJECT = ProjectId("project-1")
        val INVOICE = InvoiceId("invoice-1")
        val QUOTE = QuoteId("quote-1")
        val CONTRACT = ContractId("contract-1")
    }
}
