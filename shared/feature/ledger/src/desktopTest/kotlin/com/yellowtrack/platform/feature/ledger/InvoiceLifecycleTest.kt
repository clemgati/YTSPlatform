package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
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
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.ledger.presentation.LedgerContent
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * An invoice from raised to settled, and the two ways it can be taken back.
 *
 * The distinction under test throughout is between a document nobody has seen, which may
 * simply go, and one a client is holding, which may only be cancelled in a way that leaves
 * a trace.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceLifecycleTest {
    private val usd = CurrencyCode.USD
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID
    private val now = TestAppClock.DEFAULT_NOW

    private val clientId = ClientId.new()
    private val projectId = ProjectId.new()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Harness(
        val clock: TestAppClock,
        val invoices: FakeInvoiceRepository,
        val quotes: FakeQuoteRepository,
        val viewModel: LedgerViewModel,
    )

    private fun harness(
        invoices: FakeInvoiceRepository = FakeInvoiceRepository(),
        quotes: FakeQuoteRepository = FakeQuoteRepository(),
    ): Harness {
        val clock = TestAppClock()
        val expenses = FakeExpenseRepository()

        return Harness(
            clock = clock,
            invoices = invoices,
            quotes = quotes,
            viewModel =
                LedgerViewModel(
                    invoiceRepository = invoices,
                    quoteRepository = quotes,
                    contractRepository = FakeContractRepository(),
                    expenseRepository = expenses,
                    codbRepository = FakeCodbRepository(expenses = expenses),
                    serviceTemplateRepository = FakeServiceTemplateRepository(),
                    projectRepository = FakeProjectRepository(listOf(project())),
                    sessionRepository = FakeSessionRepository(),
                    postProductionRepository = FakePostProductionRepository(),
                    clientRepository = FakeClientRepository(listOf(client())),
                    studioProfileRepository = FakeStudioProfileRepository(),
                    documentSink = RecordingDocumentSink(),
                    documentSender = RecordingDocumentSender(),
                    studioContext = LocalStudioContext(),
                    clock = clock,
                    timeZone = TimeZone.UTC,
                ),
        )
    }

    private fun client() =
        Client(
            id = clientId,
            studioId = studioId,
            accountName = "Sarah & Michael Johnson",
            accountType = ClientAccountType.Couple,
            audit = AuditMetadata.createdAt(now),
        )

    private fun project() =
        Project(
            id = projectId,
            studioId = studioId,
            clientId = clientId,
            name = "Johnson Wedding",
            serviceLine = ServiceLine.Wedding,
            status = ProjectStatus.Enquiry,
            audit = AuditMetadata.createdAt(now),
        )

    private fun invoice(
        number: String = "INV-001",
        status: InvoiceStatus = InvoiceStatus.Draft,
        paid: Money? = null,
        dueAt: kotlin.time.Instant? = TestAppClock.DEFAULT_NOW + 14.days,
    ): Invoice {
        val id = InvoiceId.new()

        return Invoice(
            id = id,
            studioId = studioId,
            projectId = projectId,
            number = number,
            kind = InvoiceKind.Full,
            status = status,
            currency = usd,
            lines = listOf(LineItem("Coverage", Money.ofMajor(4_000, usd))),
            payments =
                listOfNotNull(
                    paid?.let {
                        Payment(
                            id = PaymentId.new(),
                            studioId = studioId,
                            invoiceId = id,
                            amount = it,
                            paidAt = now,
                            method = PaymentMethod.BankTransfer,
                            audit = AuditMetadata.createdAt(now),
                        )
                    },
                ),
            issuedAt = now.takeIf { status != InvoiceStatus.Draft },
            dueAt = dueAt,
            audit = AuditMetadata.createdAt(now),
        )
    }

    private suspend fun Harness.content(): LedgerContent {
        val state = viewModel.uiState.first { it.content is UiState.Success }
        return (state.content as UiState.Success).data
    }

    // --- A draft is visible at all -----------------------------------------------------

    @Test
    fun `a draft invoice is listed so it can be sent`() =
        runTest {
            val harness = harness(invoices = FakeInvoiceRepository(listOf(invoice())))

            val owed = harness.content().moneyOwed
            assertTrue(owed.invoices.isEmpty(), "a draft owes nothing")
            assertEquals(1, owed.drafts.size, "and must still be somewhere the studio can act on it")
            assertEquals("$4,000.00", owed.drafts.single().total)
        }

    @Test
    fun `the invoice raised by accepting a quote is reachable`() =
        runTest {
            val agreed =
                Quote(
                    id = QuoteId.new(),
                    studioId = studioId,
                    projectId = projectId,
                    number = "QUO-001",
                    status = QuoteStatus.Sent,
                    currency = usd,
                    lines = listOf(LineItem("Coverage", Money.ofMajor(4_000, usd))),
                    issuedAt = now,
                    audit = AuditMetadata.createdAt(now),
                )
            val harness = harness(quotes = FakeQuoteRepository(listOf(agreed)))

            harness.viewModel.acceptQuote(agreed.id)

            val owed = harness.content().moneyOwed
            assertEquals(
                1,
                owed.drafts.size,
                "accepting raises a draft; if it is listed nowhere the agreed work is never billed",
            )
        }

    @Test
    fun `drafts are listed oldest first`() =
        runTest {
            val older = invoice(number = "INV-001")
            val newer =
                invoice(number = "INV-002").let {
                    it.copy(audit = AuditMetadata.createdAt(now + 5.days))
                }
            val harness = harness(invoices = FakeInvoiceRepository(listOf(newer, older)))

            assertEquals(
                listOf("INV-001", "INV-002"),
                harness
                    .content()
                    .moneyOwed.drafts
                    .map { it.number },
            )
        }

    // --- Sending -----------------------------------------------------------------------

    @Test
    fun `sending a draft puts it into money owed and stamps the demand`() =
        runTest {
            val draft = invoice()
            val harness = harness(invoices = FakeInvoiceRepository(listOf(draft)))
            harness.clock.advanceBy(3.days)

            harness.viewModel.sendInvoice(draft.id)

            val stored = assertNotNull(harness.invoices.getInvoice(draft.id))
            assertEquals(InvoiceStatus.Sent, stored.status)
            assertEquals(
                now + 3.days,
                stored.issuedAt,
                "the clock a client is held to runs from the demand they received",
            )
            assertEquals(Money.ofMajor(4_000, usd), stored.outstanding(now + 3.days))
        }

    @Test
    fun `sending an already sent invoice does not reissue it`() =
        runTest {
            val sent = invoice(status = InvoiceStatus.Sent)
            val harness = harness(invoices = FakeInvoiceRepository(listOf(sent)))
            harness.clock.advanceBy(3.days)

            harness.viewModel.sendInvoice(sent.id)

            assertEquals(now, assertNotNull(harness.invoices.getInvoice(sent.id)).issuedAt)
        }

    // --- Voiding -----------------------------------------------------------------------

    @Test
    fun `voiding a sent invoice keeps the row and takes it out of money owed`() =
        runTest {
            val sent = invoice(status = InvoiceStatus.Sent)
            val harness = harness(invoices = FakeInvoiceRepository(listOf(sent)))

            harness.viewModel.voidInvoice(sent.id)

            val stored = assertNotNull(harness.invoices.getInvoice(sent.id))
            assertEquals(InvoiceStatus.Void, stored.status, "the number stays used, so it is never reissued")
            assertTrue(stored.outstanding(now).isZero)
            assertTrue(
                harness
                    .content()
                    .moneyOwed.invoices
                    .isEmpty(),
            )
        }

    @Test
    fun `an invoice with money against it cannot be voided`() =
        runTest {
            val partPaid = invoice(status = InvoiceStatus.Sent, paid = Money.ofMajor(1_000, usd))
            val harness = harness(invoices = FakeInvoiceRepository(listOf(partPaid)))

            harness.viewModel.voidInvoice(partPaid.id)

            assertEquals(
                InvoiceStatus.Sent,
                assertNotNull(harness.invoices.getInvoice(partPaid.id)).status,
                "voiding would take a payment the studio actually received out of the books",
            )
        }

    @Test
    fun `a part-paid invoice is not offered the option to void`() =
        runTest {
            val partPaid = invoice(status = InvoiceStatus.Sent, paid = Money.ofMajor(1_000, usd))
            val harness = harness(invoices = FakeInvoiceRepository(listOf(partPaid)))

            assertFalse(
                harness
                    .content()
                    .moneyOwed.invoices
                    .single()
                    .canVoid,
            )
        }

    @Test
    fun `an unpaid invoice is offered the option to void`() =
        runTest {
            val harness = harness(invoices = FakeInvoiceRepository(listOf(invoice(status = InvoiceStatus.Sent))))

            assertTrue(
                harness
                    .content()
                    .moneyOwed.invoices
                    .single()
                    .canVoid,
            )
        }

    // --- Discarding --------------------------------------------------------------------

    @Test
    fun `a draft can be discarded outright`() =
        runTest {
            val draft = invoice()
            val harness = harness(invoices = FakeInvoiceRepository(listOf(draft)))

            harness.viewModel.deleteInvoice(draft.id)

            assertNull(harness.invoices.getInvoice(draft.id))
        }

    @Test
    fun `an invoice a client is holding is never deleted`() =
        runTest {
            val sent = invoice(status = InvoiceStatus.Sent)
            val harness = harness(invoices = FakeInvoiceRepository(listOf(sent)))

            harness.viewModel.deleteInvoice(sent.id)

            assertNotNull(
                harness.invoices.getInvoice(sent.id),
                "deleting it would leave a client holding a number the studio has no record of",
            )
        }

    @Test
    fun `a voided invoice is not deleted either`() =
        runTest {
            val voided = invoice(status = InvoiceStatus.Void)
            val harness = harness(invoices = FakeInvoiceRepository(listOf(voided)))

            harness.viewModel.deleteInvoice(voided.id)

            assertNotNull(harness.invoices.getInvoice(voided.id), "it is retained precisely so the number stays used")
        }

    // --- Numbering ---------------------------------------------------------------------

    @Test
    fun `a voided invoice still holds its number against reissue`() =
        runTest {
            val voided = invoice(number = "INV-008", status = InvoiceStatus.Sent)
            val harness = harness(invoices = FakeInvoiceRepository(listOf(voided)))

            harness.viewModel.voidInvoice(voided.id)

            assertEquals(
                "INV-009",
                harness
                    .content()
                    .proposals.nextInvoiceNumber,
                "a client holding INV-008 must never meet a second INV-008",
            )
        }

    @Test
    fun `a discarded draft releases its number, which nobody ever saw`() =
        runTest {
            val draft = invoice(number = "INV-008")
            val harness = harness(invoices = FakeInvoiceRepository(listOf(draft)))

            harness.viewModel.deleteInvoice(draft.id)

            assertEquals(
                "INV-001",
                harness
                    .content()
                    .proposals.nextInvoiceNumber,
                "an unsent number was never quoted to anyone, so reusing it misleads no one",
            )
        }
}
