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
import com.yellowtrack.platform.core.model.invoice.PaymentState
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
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeQuoteRepository
import com.yellowtrack.platform.core.testing.FakeServiceTemplateRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class ProposalActionsTest {
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
        val quotes: FakeQuoteRepository,
        val invoices: FakeInvoiceRepository,
        val contracts: FakeContractRepository,
        val viewModel: LedgerViewModel,
    )

    private fun harness(
        quotes: FakeQuoteRepository = FakeQuoteRepository(),
        invoices: FakeInvoiceRepository = FakeInvoiceRepository(),
        contracts: FakeContractRepository = FakeContractRepository(),
    ): Harness {
        val clock = TestAppClock()
        val expenses = FakeExpenseRepository()

        return Harness(
            clock = clock,
            quotes = quotes,
            invoices = invoices,
            contracts = contracts,
            viewModel =
                LedgerViewModel(
                    invoiceRepository = invoices,
                    quoteRepository = quotes,
                    contractRepository = contracts,
                    expenseRepository = expenses,
                    codbRepository = FakeCodbRepository(expenses = expenses),
                    serviceTemplateRepository = FakeServiceTemplateRepository(),
                    projectRepository = FakeProjectRepository(listOf(project())),
                    clientRepository = FakeClientRepository(listOf(client())),
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

    private fun quote(
        number: String = "QUO-001",
        status: QuoteStatus = QuoteStatus.Sent,
        lines: List<LineItem> = listOf(LineItem("Coverage", Money.ofMajor(4_000, CurrencyCode.USD))),
        issuedAt: kotlin.time.Instant? = TestAppClock.DEFAULT_NOW,
        validUntil: kotlin.time.Instant? = null,
    ) = Quote(
        id = QuoteId.new(),
        studioId = studioId,
        projectId = projectId,
        number = number,
        status = status,
        currency = CurrencyCode.USD,
        lines = lines,
        issuedAt = issuedAt,
        validUntil = validUntil,
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    private fun newQuote(
        number: String = "QUO-001",
        amount: String = "4000",
        taxRate: String = "",
        validUntil: String = "2026-08-12",
        lines: List<NewLineItem> =
            listOf(
                NewLineItem(
                    description = "Wedding coverage, eight hours",
                    unitPrice = amount,
                    taxRate = taxRate,
                ),
            ),
    ) = NewQuote(
        number = number,
        projectId = projectId,
        lines = lines,
        validUntil = validUntil,
        terms = null,
    )

    private fun newInvoice(
        number: String = "INV-001",
        amount: String = "2500",
        taxRate: String = "",
        dueOn: String = "2026-06-27",
        sendNow: Boolean = true,
        kind: InvoiceKind = InvoiceKind.Balance,
        lines: List<NewLineItem> =
            listOf(
                NewLineItem(
                    description = "Balance of wedding coverage",
                    unitPrice = amount,
                    taxRate = taxRate,
                ),
            ),
    ) = NewInvoice(
        number = number,
        projectId = projectId,
        kind = kind,
        lines = lines,
        dueOn = dueOn,
        sendNow = sendNow,
    )

    // --- Quoting -----------------------------------------------------------------------

    @Test
    fun `a quote is sent rather than left as a draft nobody sees`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(newQuote())

            val stored =
                harness.quotes
                    .observeQuotes()
                    .first()
                    .single()
            assertEquals(QuoteStatus.Sent, stored.status)
            assertEquals(now, stored.issuedAt)
            assertEquals(Money.ofMajor(4_000, usd), stored.total)
        }

    @Test
    fun `a quote with no validity date is stored without one rather than rejected`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(newQuote(validUntil = ""))

            assertNull(
                harness.quotes
                    .observeQuotes()
                    .first()
                    .single()
                    .validUntil,
            )
        }

    @Test
    fun `a quote with an unparseable amount is not stored`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(newQuote(amount = "four thousand"))

            assertTrue(
                harness.quotes
                    .observeQuotes()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `a blank tax rate means no tax rather than a rejected quote`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(newQuote(taxRate = ""))

            val stored =
                harness.quotes
                    .observeQuotes()
                    .first()
                    .single()
            assertEquals(0, stored.lines.single().taxRateBasisPoints)
            assertTrue(stored.tax.isZero)
        }

    @Test
    fun `a stated tax rate is held in basis points`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(newQuote(taxRate = "8.25"))

            val stored =
                harness.quotes
                    .observeQuotes()
                    .first()
                    .single()
            assertEquals(825, stored.lines.single().taxRateBasisPoints)
            assertEquals(Money(33_000, usd), stored.tax)
        }

    // --- Several lines -----------------------------------------------------------------

    @Test
    fun `a quote carries every line it was given, in the order they were entered`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(
                newQuote(
                    lines =
                        listOf(
                            NewLineItem(description = "Wedding coverage, eight hours", unitPrice = "4000"),
                            NewLineItem(description = "Second shooter", unitPrice = "600"),
                            NewLineItem(description = "Album", unitPrice = "450"),
                        ),
                ),
            )

            val stored =
                harness.quotes
                    .observeQuotes()
                    .first()
                    .single()
            assertEquals(
                listOf("Wedding coverage, eight hours", "Second shooter", "Album"),
                stored.lines.map { it.description },
                "a client reads the lines as sent, not reordered",
            )
            assertEquals(Money.ofMajor(5_050, usd), stored.total)
        }

    @Test
    fun `a quantity multiplies the line rather than being ignored`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(
                newQuote(
                    lines = listOf(NewLineItem(description = "Extra hour", quantity = "3", unitPrice = "250")),
                ),
            )

            val line =
                harness.quotes
                    .observeQuotes()
                    .first()
                    .single()
                    .lines
                    .single()
            assertEquals(3, line.quantity)
            assertEquals(Money.ofMajor(750, usd), line.subtotal)
        }

    @Test
    fun `tax is charged per line, so a mixed-rate document totals correctly`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(
                newQuote(
                    lines =
                        listOf(
                            NewLineItem(description = "Coverage", unitPrice = "4000"),
                            NewLineItem(description = "Album", unitPrice = "500", taxRate = "8.25"),
                        ),
                ),
            )

            val stored =
                harness.quotes
                    .observeQuotes()
                    .first()
                    .single()
            assertEquals(Money.ofMajor(4_500, usd), stored.subtotal)
            assertEquals(Money(4_125, usd), stored.tax, "only the album is taxed")
            assertEquals(Money(454_125, usd), stored.total)
        }

    @Test
    fun `one bad line rejects the whole document rather than being dropped`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(
                newQuote(
                    lines =
                        listOf(
                            NewLineItem(description = "Coverage", unitPrice = "4000"),
                            NewLineItem(description = "Album", unitPrice = "four hundred"),
                        ),
                ),
            )

            assertTrue(
                harness.quotes
                    .observeQuotes()
                    .first()
                    .isEmpty(),
                "billing for less than was entered, silently, is worse than refusing to save",
            )
        }

    @Test
    fun `a line with no description is not a billable line`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(
                newQuote(lines = listOf(NewLineItem(description = "  ", unitPrice = "4000"))),
            )

            assertTrue(
                harness.quotes
                    .observeQuotes()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `a document with no lines at all is not stored`() =
        runTest {
            val harness = harness()

            harness.viewModel.addQuote(newQuote(lines = emptyList()))
            harness.viewModel.addInvoice(newInvoice(lines = emptyList()))

            assertTrue(
                harness.quotes
                    .observeQuotes()
                    .first()
                    .isEmpty(),
            )
            assertTrue(
                harness.invoices
                    .observeInvoices()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `an invoice carries several lines too`() =
        runTest {
            val harness = harness()

            harness.viewModel.addInvoice(
                newInvoice(
                    lines =
                        listOf(
                            NewLineItem(description = "Balance of coverage", unitPrice = "2000"),
                            NewLineItem(description = "Travel", unitPrice = "150"),
                        ),
                ),
            )

            val stored =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(2, stored.lines.size)
            assertEquals(Money.ofMajor(2_150, usd), stored.total)
        }

    @Test
    fun `accepting a multi-line quote bills every line that was agreed`() =
        runTest {
            val agreed =
                quote(
                    lines =
                        listOf(
                            LineItem("Coverage", Money.ofMajor(4_000, usd)),
                            LineItem("Second shooter", Money.ofMajor(600, usd)),
                            LineItem("Extra hour", Money.ofMajor(250, usd), quantity = 2),
                        ),
                )
            val harness = harness(quotes = FakeQuoteRepository(listOf(agreed)))

            harness.viewModel.acceptQuote(agreed.id)

            val raised =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(agreed.lines, raised.lines, "re-entering lines by hand is where figures diverge")
            assertEquals(Money.ofMajor(5_100, usd), raised.total)
        }

    // --- Accepting ---------------------------------------------------------------------

    @Test
    fun `accepting a quote raises an invoice for exactly the quoted figure`() =
        runTest {
            val agreed =
                quote(lines = listOf(LineItem("Coverage", Money.ofMajor(4_000, usd), taxRateBasisPoints = 825)))
            val harness = harness(quotes = FakeQuoteRepository(listOf(agreed)))

            harness.viewModel.acceptQuote(agreed.id)

            val raised =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(agreed.lines, raised.lines)
            assertEquals(agreed.total, raised.total)
            assertEquals(agreed.projectId, raised.projectId)
        }

    @Test
    fun `an invoice raised on acceptance is a draft and owes nothing yet`() =
        runTest {
            val agreed = quote()
            val harness = harness(quotes = FakeQuoteRepository(listOf(agreed)))

            harness.viewModel.acceptQuote(agreed.id)

            val raised =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(InvoiceStatus.Draft, raised.status)
            assertEquals(PaymentState.Draft, raised.paymentState(now))
            assertTrue(raised.outstanding(now).isZero)
        }

    @Test
    fun `an invoice raised on acceptance continues the studio's numbering`() =
        runTest {
            val agreed = quote()
            val existing =
                Invoice(
                    id = InvoiceId.new(),
                    studioId = studioId,
                    projectId = projectId,
                    number = "INV-007",
                    kind = InvoiceKind.Retainer,
                    status = InvoiceStatus.Sent,
                    currency = usd,
                    lines = listOf(LineItem("Retainer", Money.ofMajor(2_000, usd))),
                    audit = AuditMetadata.createdAt(now),
                )
            val harness =
                harness(
                    quotes = FakeQuoteRepository(listOf(agreed)),
                    invoices = FakeInvoiceRepository(listOf(existing)),
                )

            harness.viewModel.acceptQuote(agreed.id)

            val raised =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .first { it.number != "INV-007" }
            assertEquals("INV-008", raised.number)
        }

    @Test
    fun `accepting stamps the quote and takes it off the decision list`() =
        runTest {
            val agreed = quote()
            val harness = harness(quotes = FakeQuoteRepository(listOf(agreed)))
            harness.clock.advanceBy(2.days)

            harness.viewModel.acceptQuote(agreed.id)

            val stored = assertNotNull(harness.quotes.getQuote(agreed.id))
            assertEquals(QuoteStatus.Accepted, stored.status)
            assertEquals(now + 2.days, stored.acceptedAt)
            assertTrue(
                harness.quotes
                    .observeAwaitingDecision()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `declining a quote records no invoice`() =
        runTest {
            val proposal = quote()
            val harness = harness(quotes = FakeQuoteRepository(listOf(proposal)))

            harness.viewModel.declineQuote(proposal.id)

            val stored = assertNotNull(harness.quotes.getQuote(proposal.id))
            assertEquals(QuoteStatus.Declined, stored.status)
            assertNull(stored.acceptedAt)
            assertTrue(
                harness.invoices
                    .observeInvoices()
                    .first()
                    .isEmpty(),
                "a declined quote must never bill the client",
            )
        }

    @Test
    fun `accepting a quote that is not there does nothing`() =
        runTest {
            val harness = harness()

            harness.viewModel.acceptQuote(QuoteId.new())

            assertTrue(
                harness.invoices
                    .observeInvoices()
                    .first()
                    .isEmpty(),
            )
        }

    // --- Invoicing ---------------------------------------------------------------------

    @Test
    fun `an invoice sent now counts toward money owed`() =
        runTest {
            val harness = harness()

            harness.viewModel.addInvoice(newInvoice(sendNow = true))

            val stored =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(InvoiceStatus.Sent, stored.status)
            assertEquals(now, stored.issuedAt)
            assertEquals(Money.ofMajor(2_500, usd), stored.outstanding(now))
        }

    @Test
    fun `an invoice saved as a draft has no issue date and owes nothing`() =
        runTest {
            val harness = harness()

            harness.viewModel.addInvoice(newInvoice(sendNow = false, dueOn = "2026-06-01"))

            val stored =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(InvoiceStatus.Draft, stored.status)
            assertNull(stored.issuedAt, "an unissued invoice has not been demanded yet")
            assertTrue(
                stored.outstanding(now).isZero,
                "a draft past its due date must not read as overdue",
            )
        }

    @Test
    fun `an invoice with an unparseable due date is not stored`() =
        runTest {
            val harness = harness()

            harness.viewModel.addInvoice(newInvoice(dueOn = "next Friday"))

            assertTrue(
                harness.invoices
                    .observeInvoices()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `an invoice with a zero amount is not stored`() =
        runTest {
            val harness = harness()

            harness.viewModel.addInvoice(newInvoice(amount = "0"))

            assertTrue(
                harness.invoices
                    .observeInvoices()
                    .first()
                    .isEmpty(),
            )
        }
}
