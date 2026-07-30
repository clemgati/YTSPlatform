package com.yellowtrack.platform.core.export

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.money.formatted
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
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
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.model.studio.StudioProfileId
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The two documents a client actually reads.
 *
 * What is being pinned is that the figures on the page are the ones the model computed —
 * a document arriving at a different total from the screen it was sent from is the worst
 * bug this application could have.
 */
class BillingDocumentsTest {
    private val usd = CurrencyCode.USD
    private val now = Instant.fromEpochMilliseconds(1_781_100_000_000)
    private val zone = TimeZone.UTC
    private val studioId = StudioId("studio-1")
    private val projectId = ProjectId("project-1")
    private val clientId = ClientId("client-1")

    private fun studio(
        name: String = "Yellow Track Studios",
        taxNumber: String? = "GB123456789",
        paymentInstructions: String? = "Bank transfer\nSort 00-00-00, account 12345678",
        footer: String? = "Payment due within 14 days.",
    ) = StudioProfile(
        id = StudioProfileId.new(),
        studioId = studioId,
        name = name,
        address = "12 Harbour Road\nFalmouth",
        email = "hello@yellowtrack.example",
        phone = "07700 900000",
        taxNumber = taxNumber,
        paymentInstructions = paymentInstructions,
        documentFooter = footer,
        audit = AuditMetadata.createdAt(now),
    )

    private fun client() =
        Client(
            id = clientId,
            studioId = studioId,
            accountName = "Harbourline Coffee",
            accountType = ClientAccountType.Company,
            audit = AuditMetadata.createdAt(now),
        )

    private fun project() =
        Project(
            id = projectId,
            studioId = studioId,
            clientId = clientId,
            name = "Autumn Brand Shoot",
            serviceLine = ServiceLine.Branding,
            status = ProjectStatus.Booked,
            audit = AuditMetadata.createdAt(now),
        )

    private fun lines() =
        listOf(
            LineItem("Shoot day", Money(400_000L, usd)),
            LineItem("Retouched images", Money(5_000L, usd), quantity = 20),
        )

    private fun invoice(
        status: InvoiceStatus = InvoiceStatus.Sent,
        payments: List<Payment> = emptyList(),
        dueAt: Instant? = now + 14.days,
    ) = Invoice(
        id = InvoiceId.new(),
        studioId = studioId,
        projectId = projectId,
        number = "INV-004",
        kind = InvoiceKind.Full,
        status = status,
        currency = usd,
        lines = lines(),
        payments = payments,
        issuedAt = now,
        dueAt = dueAt,
        audit = AuditMetadata.createdAt(now),
    )

    private fun payment(amount: Long) =
        Payment(
            id = PaymentId.new(),
            studioId = studioId,
            invoiceId = InvoiceId.new(),
            amount = Money(amount, usd),
            paidAt = now,
            method = PaymentMethod.BankTransfer,
            audit = AuditMetadata.createdAt(now),
        )

    private fun sheet(
        invoice: Invoice = invoice(),
        studio: StudioProfile = studio(),
    ) = buildInvoice(invoice, project(), client(), studio, now, zone)

    private fun Sheet.section(heading: String): SheetSection? = sections.firstOrNull { it.heading == heading }

    private fun Sheet.facts(heading: String): List<SheetFact> =
        section(heading)
            ?.blocks
            ?.filterIsInstance<SheetBlock.Facts>()
            ?.flatMap { it.facts }
            .orEmpty()

    /** Reads both kinds of text block, so a test does not care which one a section used. */
    private fun Sheet.lines(heading: String): List<String> =
        section(heading)
            ?.blocks
            ?.flatMap { block ->
                when (block) {
                    is SheetBlock.Lines -> block.lines
                    is SheetBlock.Paragraphs -> block.paragraphs
                    else -> emptyList()
                }
            }.orEmpty()

    // --- The figures ---------------------------------------------------------------------

    @Test
    fun `the totals on the page are the ones the invoice computed`() {
        val invoice = invoice()
        val totals = sheet(invoice).facts("Total")

        assertEquals(invoice.total.formatted(), totals.first { it.label == "Total" }.value)
        assertEquals(invoice.subtotal.formatted(), totals.first { it.label == "Subtotal" }.value)
    }

    @Test
    fun `the balance due is what the client is looking for and is the figure in bold`() {
        val totals = sheet(invoice(payments = listOf(payment(200_000L)))).facts("Total")

        val balance = assertNotNull(totals.firstOrNull { it.label == "Balance due" })
        assertEquals("$3,000.00", balance.value)
        assertTrue(balance.isEmphasised)
    }

    @Test
    fun `a part payment is shown so the client can see it landed`() {
        val totals = sheet(invoice(payments = listOf(payment(200_000L)))).facts("Total")

        assertEquals("$2,000.00", assertNotNull(totals.firstOrNull { it.label == "Paid" }).value)
    }

    @Test
    fun `an overpayment reads as a refund owed rather than a negative balance`() {
        val totals = sheet(invoice(payments = listOf(payment(600_000L)))).facts("Total")

        val overpaid = assertNotNull(totals.firstOrNull { it.label == "Overpaid" })
        assertEquals("$1,000.00", overpaid.value, "a client reading minus one thousand due starts an argument")
    }

    @Test
    fun `a line of one is not padded with a quantity nobody needs to read`() {
        val entries =
            sheet()
                .section("Work")
                ?.blocks
                ?.filterIsInstance<SheetBlock.Entries>()
                ?.flatMap { it.entries }
                .orEmpty()

        assertNull(entries.first { it.name == "Shoot day" }.detail)
        assertEquals("20 × $50.00", entries.first { it.name == "Retouched images" }.detail)
    }

    // --- What the studio must supply ---------------------------------------------------------

    @Test
    fun `the studio's details are on the invoice with the tax number`() {
        val from = sheet().lines("From")

        assertTrue(from.contains("Yellow Track Studios"))
        assertTrue(from.contains("12 Harbour Road"), "an address on one run-on line is not an address")
        assertTrue(from.contains("Falmouth"))
        assertTrue(from.contains("Tax registration GB123456789"))
    }

    @Test
    fun `an invoice with no tax number simply omits the line`() {
        val from = sheet(studio = studio(taxNumber = null)).lines("From")

        assertFalse(from.any { it.contains("Tax registration") })
    }

    @Test
    fun `the footer travels with the document rather than becoming a section`() {
        val invoiceSheet = sheet()

        assertEquals("Payment due within 14 days.", invoiceSheet.footer)
        assertNull(invoiceSheet.section("Footer"), "the small print should look like small print")
    }

    // --- How to pay ----------------------------------------------------------------------------

    @Test
    fun `how to pay is on an invoice that is still owed`() {
        assertTrue(sheet().lines("How to pay").contains("Sort 00-00-00, account 12345678"))
    }

    @Test
    fun `a settled invoice does not invite a second payment`() {
        val settled = sheet(invoice(payments = listOf(payment(500_000L))))

        assertNull(
            settled.section("How to pay"),
            "bank details on a paid invoice buy a refund, an apology and an afternoon",
        )
        assertEquals("Paid in full — thank you", settled.facts("This invoice").first { it.label == "Status" }.value)
    }

    @Test
    fun `a studio that has not said how to pay does not get an empty heading`() {
        assertNull(sheet(studio = studio(paymentInstructions = null)).section("How to pay"))
    }

    // --- Status ----------------------------------------------------------------------------------

    @Test
    fun `an ordinary unpaid invoice says nothing about status`() {
        val status = sheet().facts("This invoice").firstOrNull { it.label == "Status" }

        assertNull(status, "the balance already says it, and repeating it teaches the reader to skip the block")
    }

    @Test
    fun `an overdue invoice says so`() {
        val overdue = sheet(invoice(dueAt = now - 5.days))

        assertEquals("Overdue", overdue.facts("This invoice").first { it.label == "Status" }.value)
    }

    @Test
    fun `the due date on a paid invoice is not a demand in bold`() {
        val paid = sheet(invoice(payments = listOf(payment(500_000L))))

        assertFalse(paid.facts("This invoice").first { it.label == "Due" }.isEmphasised)
    }

    // --- Who it is addressed to ---------------------------------------------------------------------

    @Test
    fun `the invoice is addressed to the client and names the booking`() {
        val to = sheet().lines("To")

        assertTrue(to.contains("Harbourline Coffee"))
        assertTrue(to.contains("For: Autumn Brand Shoot"))
    }

    @Test
    fun `an invoice with no client on file has no empty To heading`() {
        val orphan = buildInvoice(invoice(), null, null, studio(), now, zone)

        assertNull(orphan.section("To"))
    }

    // --- Quotes -------------------------------------------------------------------------------------

    private fun quote(
        status: QuoteStatus = QuoteStatus.Sent,
        validUntil: Instant? = now + 30.days,
    ) = Quote(
        id = QuoteId.new(),
        studioId = studioId,
        projectId = projectId,
        number = "QUO-002",
        status = status,
        currency = usd,
        lines = lines(),
        issuedAt = now,
        validUntil = validUntil,
        terms = "Fifty per cent on booking.",
        audit = AuditMetadata.createdAt(now),
    )

    @Test
    fun `a quote leads with the date the price stops standing`() {
        val facts = buildQuote(quote(), project(), client(), studio(), now, zone).facts("This quote")

        val validity = assertNotNull(facts.firstOrNull { it.label == "Valid until" })
        assertTrue(validity.isEmphasised, "a quote with no visible expiry is a price offered forever")
    }

    @Test
    fun `a quote past its date says expired rather than still valid`() {
        val stale = quote(validUntil = now - 1.days)
        val facts = buildQuote(stale, project(), client(), studio(), now, zone).facts("This quote")

        assertNotNull(facts.firstOrNull { it.label == "Expired" })
        assertEquals("This quote has expired", facts.first { it.label == "Status" }.value)
    }

    @Test
    fun `a quote carries its terms as their own section`() {
        val terms = buildQuote(quote(), project(), client(), studio(), now, zone).lines("Terms")

        assertEquals(listOf("Fifty per cent on booking."), terms)
    }

    @Test
    fun `a quote shows no balance due because nothing is owed on it yet`() {
        val totals = buildQuote(quote(), project(), client(), studio(), now, zone).facts("Total")

        assertTrue(totals.none { it.label == "Balance due" })
        assertTrue(totals.first { it.label == "Total" }.isEmphasised)
    }
}
