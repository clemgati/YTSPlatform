package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.InvoiceRepository
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.data.sync.WriteFailed
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.model.studio.StudioProfileId
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * Sending an invoice, and what happens when the studio has not said who it is.
 *
 * The rule being pinned is that a nameless studio cannot issue a document and is told why.
 * A button that silently does nothing is worse than one that explains itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentExportTest {
    private val usd = CurrencyCode.USD
    private val now = TestAppClock.DEFAULT_NOW
    private val studioId = LocalStudioContext.LOCAL_STUDIO_ID
    private val clientId = ClientId.new()
    private val projectId = ProjectId.new()
    private val invoiceId = InvoiceId.new()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Harness(
        val viewModel: LedgerViewModel,
        val sink: RecordingDocumentSink,
        val sender: RecordingDocumentSender,
    )

    /**
     * ADR 0012 made a ledger write able to fail. Without this the exception escapes into
     * `viewModelScope` and the studio sees a form close on a save that never happened — the
     * failure the whole decision exists to avoid.
     */
    @Test
    fun `a write that could not reach the server is reported rather than thrown away`() =
        runTest {
            val harness = harness(invoiceWrites = FailingInvoiceWrites)

            harness.viewModel.removePayment(PaymentId.new())

            assertEquals(
                WriteFailed.Offline.message,
                harness.viewModel.writeFailureMessage.value,
                "a studio told nothing has an invoice that exists on its screen and nowhere else",
            )
        }

    // -- Emailing it, rather than saving it ---------------------------------------------

    @Test
    fun `emails the rendered document to the address the studio typed`() =
        runTest {
            val harness = harness()
            var message: String? = null

            harness.viewModel.emailDocument(
                to = "client@example.com",
                sheet = { harness.viewModel.invoiceSheet(invoiceId) },
                onResult = { message = it },
            )

            val sent = assertNotNull(harness.sender.last, "nothing was sent")
            assertEquals("client@example.com", sent.to)
            assertTrue(sent.html.contains("<"), "the client should get the page, not a description of it")
            assertTrue(sent.text.isNotBlank(), "a client that cannot render html still needs the document")
            assertTrue(sent.subject.isNotBlank(), "an email with no subject is an email nobody opens")
            assertEquals("Sent to client@example.com. A copy is in your inbox.", message)
        }

    /**
     * Every refusal here is something the studio can act on — no studio email, a daily limit,
     * a mail server that would not take it. Flattening them to "that did not work" throws
     * away the only useful part.
     */
    @Test
    fun `shows the server's own words when a send is refused`() =
        runTest {
            val harness = harness(refusal = "Add your studio's email address in Settings first.")
            var message: String? = null

            harness.viewModel.emailDocument(
                to = "client@example.com",
                sheet = { harness.viewModel.invoiceSheet(invoiceId) },
                onResult = { message = it },
            )

            assertEquals("Add your studio's email address in Settings first.", message)
        }

    private fun harness(
        profile: StudioProfile? = studioProfile(),
        invoices: List<Invoice> = listOf(invoice()),
        refusal: String? = null,
        invoiceWrites: InvoiceRepository? = null,
    ): Harness {
        val sink = RecordingDocumentSink()
        val sender = RecordingDocumentSender(refusal = refusal)
        val expenses = FakeExpenseRepository()

        return Harness(
            viewModel =
                LedgerViewModel(
                    invoiceRepository = invoiceWrites ?: FakeInvoiceRepository(invoices),
                    quoteRepository = FakeQuoteRepository(),
                    contractRepository = FakeContractRepository(),
                    expenseRepository = expenses,
                    codbRepository = FakeCodbRepository(expenses = expenses),
                    serviceTemplateRepository = FakeServiceTemplateRepository(),
                    projectRepository = FakeProjectRepository(listOf(project())),
                    sessionRepository = FakeSessionRepository(),
                    postProductionRepository = FakePostProductionRepository(),
                    clientRepository = FakeClientRepository(listOf(client())),
                    studioProfileRepository = FakeStudioProfileRepository(profile),
                    documentSink = sink,
                    documentSender = sender,
                    studioContext = LocalStudioContext(),
                    clock = TestAppClock(),
                    timeZone = TimeZone.UTC,
                ),
            sink = sink,
            sender = sender,
        )
    }

    private fun studioProfile(
        name: String = "Yellow Track Studios",
        currency: CurrencyCode = usd,
    ) = StudioProfile(
        id = StudioProfileId.new(),
        studioId = studioId,
        name = name,
        address = "12 Harbour Road",
        paymentInstructions = "Bank transfer, sort 00-00-00",
        currency = currency,
        audit = AuditMetadata.createdAt(now),
    )

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
            status = ProjectStatus.Booked,
            audit = AuditMetadata.createdAt(now),
        )

    private fun invoice(
        id: InvoiceId = invoiceId,
        currency: CurrencyCode = usd,
    ) = Invoice(
        id = id,
        studioId = studioId,
        projectId = projectId,
        number = "INV-004",
        kind = InvoiceKind.Balance,
        status = InvoiceStatus.Sent,
        currency = currency,
        lines = listOf(LineItem("Wedding coverage", Money(400_000L, currency))),
        issuedAt = now,
        dueAt = now + 14.days,
        audit = AuditMetadata.createdAt(now),
    )

    // --- The happy path ------------------------------------------------------------------

    @Test
    fun `saving an invoice writes a web page named after it`() =
        runTest {
            val harness = harness()
            var message: String? = null

            harness.viewModel.saveDocument({ harness.viewModel.invoiceSheet(invoiceId) }) { message = it }

            val document = assertNotNull(harness.sink.last)
            assertEquals("invoice-inv-004.html", document.fileName)
            assertEquals("Saved to recorded/invoice-inv-004.html", message)
        }

    @Test
    fun `the saved invoice carries the studio, the client, and the figure owed`() =
        runTest {
            val harness = harness()

            harness.viewModel.saveDocument({ harness.viewModel.invoiceSheet(invoiceId) }) {}

            val html = assertNotNull(harness.sink.last).content
            assertTrue(html.contains("Yellow Track Studios"))
            assertTrue(html.contains("Sarah &amp; Michael Johnson"))
            assertTrue(html.contains("$4,000.00"))
            assertTrue(html.contains("Bank transfer, sort 00-00-00"), "an invoice with no way to pay it is useless")
        }

    // --- What stops a document ---------------------------------------------------------------

    @Test
    fun `a studio with no details on file is told what to do rather than nothing`() =
        runTest {
            val harness = harness(profile = null)
            var message: String? = null

            harness.viewModel.saveDocument({ harness.viewModel.invoiceSheet(invoiceId) }) { message = it }

            assertTrue(harness.sink.documents.isEmpty(), "a nameless invoice is not an invoice")
            assertEquals("Add your studio's name in Settings before sending anything out.", message)
        }

    @Test
    fun `a saved but nameless profile is the same as none`() =
        runTest {
            val harness = harness(profile = studioProfile(name = ""))
            var message: String? = null

            harness.viewModel.saveDocument({ harness.viewModel.invoiceSheet(invoiceId) }) { message = it }

            assertTrue(harness.sink.documents.isEmpty())
            assertEquals("Add your studio's name in Settings before sending anything out.", message)
        }

    @Test
    fun `nothing blocks a studio that has filled its details in`() =
        runTest {
            assertNull(harness().viewModel.documentBlocker())
        }

    // --- What the studio charges in ------------------------------------------------------

    @Test
    fun `a studio charging in pounds sees pounds on its screen`() =
        runTest {
            val harness = harness(profile = studioProfile(currency = CurrencyCode.GBP))

            val content = harness.viewModel.uiState.first { it.content is UiState.Success }

            assertEquals(
                CurrencyCode.GBP,
                (content.content as UiState.Success).data.currency,
                "CurrencyCode has said since it was written that this is a per-studio setting",
            )
        }

    @Test
    fun `a studio that has said nothing is charging in dollars rather than in nothing`() =
        runTest {
            val harness = harness(profile = null)

            val content = harness.viewModel.uiState.first { it.content is UiState.Success }

            assertEquals(usd, (content.content as UiState.Success).data.currency)
        }

    @Test
    fun `an invoice in the old currency does not break the ledger`() =
        runTest {
            // A studio that has changed what it charges in still has invoices in the old
            // one. Money refuses to add pounds to dollars — rightly — and until the totals
            // filtered, that refusal took the whole screen down.
            val harness =
                harness(
                    profile = studioProfile(currency = CurrencyCode.GBP),
                    invoices =
                        listOf(
                            invoice(currency = usd),
                            invoice(id = InvoiceId.new(), currency = CurrencyCode.GBP),
                        ),
                )

            val content = harness.viewModel.uiState.first { it.content is UiState.Success }
            val owed = (content.content as UiState.Success).data.moneyOwed

            assertEquals(2, owed.invoices.size, "both are still listed, each in its own currency")
            assertEquals(1, owed.otherCurrencyCount, "and the total says what it leaves out")
        }

    @Test
    fun `an invoice that has been deleted reports that rather than saving an empty page`() =
        runTest {
            val harness = harness()
            var message: String? = null

            harness.viewModel.saveDocument({ harness.viewModel.invoiceSheet(InvoiceId.new()) }) { message = it }

            assertTrue(harness.sink.documents.isEmpty())
            assertEquals("That document could not be read.", message)
        }
}

/** A ledger that cannot reach the server, standing in for a venue with no signal. */
private object FailingInvoiceWrites : InvoiceRepository by FakeInvoiceRepository(emptyList()) {
    override suspend fun deletePayment(paymentId: PaymentId): Unit = throw WriteFailed.Offline
}
