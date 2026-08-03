package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
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
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.feature.ledger.presentation.mapper.buildMoneyOwed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * A payment can be found after it was put against the wrong invoice.
 *
 * This is the case that hides itself. The money-owed list holds only invoices still
 * outstanding, so a payment attributed to the wrong invoice **settles** that invoice, which
 * drops it off the list — taking with it the only route back to the payment. The studio is
 * then left with an invoice it believes is paid and no way to reach the thing that paid it.
 */
class ReceivedPaymentsTest {
    @Test
    fun `a payment on a settled invoice is still listed`() {
        val settled =
            invoice("i1", "2026-014", total = 100_000)
                .copy(payments = listOf(payment("p1", "i1", 100_000)))

        val summary = moneyOwed(listOf(settled))

        assertTrue(
            summary.invoices.none { it.id == settled.id },
            "the invoice is paid, so it is correctly absent from what is owed",
        )
        assertEquals(
            listOf(PaymentId("p1")),
            summary.received.map { it.id },
            "and the payment that settled it must still be reachable, or a misattributed " +
                "payment can never be undone",
        )
    }

    @Test
    fun `a payment says which invoice and client it went to`() {
        val settled =
            invoice("i1", "2026-014", total = 100_000)
                .copy(payments = listOf(payment("p1", "i1", 100_000)))

        val received = moneyOwed(listOf(settled)).received.single()

        assertEquals(
            "Invoice 2026-014 — Okafor",
            received.against,
            "naming the invoice and the client is how a studio spots that it went to the wrong one",
        )
    }

    @Test
    fun `the newest payment is first`() {
        val invoice =
            invoice("i1", "2026-014", total = 300_000)
                .copy(
                    payments =
                        listOf(
                            payment("older", "i1", 100_000, paidAt = NOW),
                            payment("newer", "i1", 100_000, paidAt = NOW + ONE_DAY),
                        ),
                )

        assertEquals(
            listOf(PaymentId("newer"), PaymentId("older")),
            moneyOwed(listOf(invoice)).received.map { it.id },
            "a studio checking a payment is almost always checking the one just entered",
        )
    }

    @Test
    fun `payments across several invoices appear together`() {
        val first = invoice("i1", "2026-014", total = 100_000).copy(payments = listOf(payment("p1", "i1", 100_000)))
        val second = invoice("i2", "2026-015", total = 500_000).copy(payments = listOf(payment("p2", "i2", 100_000)))

        assertEquals(
            setOf(PaymentId("p1"), PaymentId("p2")),
            moneyOwed(listOf(first, second)).received.map { it.id }.toSet(),
            "one settled and one still owing — both payments are money that arrived",
        )
    }

    // -- Fixtures ----------------------------------------------------------------------------

    private fun moneyOwed(invoices: List<Invoice>) =
        buildMoneyOwed(
            invoices = invoices,
            projects = listOf(project()),
            clients = listOf(client()),
            now = NOW + ONE_DAY,
            currency = CurrencyCode.GBP,
        )

    private fun invoice(
        id: String,
        number: String,
        total: Long,
    ) = Invoice(
        id = InvoiceId(id),
        studioId = STUDIO,
        projectId = ProjectId("project-1"),
        number = number,
        kind = InvoiceKind.Balance,
        status = InvoiceStatus.Sent,
        currency = CurrencyCode.GBP,
        lines =
            listOf(
                LineItem(
                    description = "Coverage",
                    unitPrice = Money(minorUnits = total, currency = CurrencyCode.GBP),
                ),
            ),
        issuedAt = NOW,
        audit = AuditMetadata.createdAt(NOW),
    )

    private fun payment(
        id: String,
        invoiceId: String,
        minorUnits: Long,
        paidAt: Instant = NOW,
    ) = Payment(
        id = PaymentId(id),
        studioId = STUDIO,
        invoiceId = InvoiceId(invoiceId),
        amount = Money(minorUnits = minorUnits, currency = CurrencyCode.GBP),
        paidAt = paidAt,
        method = PaymentMethod.BankTransfer,
        audit = AuditMetadata.createdAt(paidAt),
    )

    private fun project() =
        Project(
            id = ProjectId("project-1"),
            studioId = STUDIO,
            clientId = ClientId("client-1"),
            name = "Okafor — Wedding",
            serviceLine = ServiceLine.Wedding,
            status = ProjectStatus.Booked,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun client() =
        Client(
            id = ClientId("client-1"),
            studioId = STUDIO,
            accountName = "Okafor",
            accountType = ClientAccountType.Couple,
            audit = AuditMetadata.createdAt(NOW),
        )

    private companion object {
        val STUDIO = StudioId("studio-1")
        val NOW: Instant = Instant.fromEpochMilliseconds(1_781_000_000_000)
        val ONE_DAY = kotlin.time.Duration.parse("24h")
    }
}
