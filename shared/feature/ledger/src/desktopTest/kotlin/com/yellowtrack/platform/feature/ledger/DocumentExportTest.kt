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
import com.yellowtrack.platform.core.testing.RecordingDocumentSink
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    )

    private fun harness(profile: StudioProfile? = studioProfile()): Harness {
        val sink = RecordingDocumentSink()
        val expenses = FakeExpenseRepository()

        return Harness(
            viewModel =
                LedgerViewModel(
                    invoiceRepository = FakeInvoiceRepository(listOf(invoice())),
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
                    studioContext = LocalStudioContext(),
                    clock = TestAppClock(),
                    timeZone = TimeZone.UTC,
                ),
            sink = sink,
        )
    }

    private fun studioProfile(name: String = "Yellow Track Studios") =
        StudioProfile(
            id = StudioProfileId.new(),
            studioId = studioId,
            name = name,
            address = "12 Harbour Road",
            paymentInstructions = "Bank transfer, sort 00-00-00",
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

    private fun invoice() =
        Invoice(
            id = invoiceId,
            studioId = studioId,
            projectId = projectId,
            number = "INV-004",
            kind = InvoiceKind.Balance,
            status = InvoiceStatus.Sent,
            currency = usd,
            lines = listOf(LineItem("Wedding coverage", Money(400_000L, usd))),
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
