package com.yellowtrack.platform.core.model.invoice

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class InvoiceTest {
    private val usd = CurrencyCode.USD
    private val now = Instant.fromEpochMilliseconds(1_781_100_000_000)
    private val studioId = StudioId("studio")
    private val projectId = ProjectId("project")

    private fun invoice(
        status: InvoiceStatus = InvoiceStatus.Sent,
        lines: List<LineItem> = listOf(LineItem("Wedding coverage", Money.ofMajor(4_500, usd))),
        payments: List<Payment> = emptyList(),
        dueAt: Instant? = now + 30.days,
    ) = Invoice(
        id = InvoiceId.new(),
        studioId = studioId,
        projectId = projectId,
        number = "INV-001",
        kind = InvoiceKind.Balance,
        status = status,
        currency = usd,
        lines = lines,
        payments = payments,
        issuedAt = now,
        dueAt = dueAt,
        audit = AuditMetadata.createdAt(now),
    )

    private fun payment(amount: Long) =
        Payment(
            id = PaymentId.new(),
            studioId = studioId,
            invoiceId = InvoiceId("invoice"),
            amount = Money.ofMajor(amount, usd),
            paidAt = now,
            method = PaymentMethod.BankTransfer,
            audit = AuditMetadata.createdAt(now),
        )

    @Test
    fun `totals lines including per-line tax`() {
        val invoice =
            invoice(
                lines =
                    listOf(
                        // Service at 0%, album at 8.25% — mixed rates are the normal case.
                        LineItem("Coverage", Money.ofMajor(4_000, usd)),
                        LineItem("Album", Money.ofMajor(600, usd), taxRateBasisPoints = 825),
                    ),
            )

        assertEquals(Money.ofMajor(4_600, usd), invoice.subtotal)
        assertEquals(Money(4_950, usd), invoice.tax)
        assertEquals(Money(464_950, usd), invoice.total)
    }

    @Test
    fun `multiplies unit price by quantity`() {
        val invoice =
            invoice(lines = listOf(LineItem("Extra hour", Money.ofMajor(250, usd), quantity = 3)))

        assertEquals(Money.ofMajor(750, usd), invoice.total)
    }

    @Test
    fun `an unpaid invoice within its terms is awaiting payment`() {
        assertEquals(PaymentState.AwaitingPayment, invoice().paymentState(now))
        assertEquals(Money.ofMajor(4_500, usd), invoice().balanceDue)
    }

    @Test
    fun `a part paid invoice reports the remaining balance`() {
        val invoice = invoice(payments = listOf(payment(1_500)))

        assertEquals(PaymentState.PartiallyPaid, invoice.paymentState(now))
        assertEquals(Money.ofMajor(3_000, usd), invoice.balanceDue)
    }

    @Test
    fun `a fully paid invoice is paid even after its due date`() {
        val invoice = invoice(payments = listOf(payment(4_500)), dueAt = now - 10.days)

        assertEquals(PaymentState.Paid, invoice.paymentState(now))
        assertTrue(invoice.balanceDue.isZero)
        assertEquals(Money.zero(usd), invoice.outstanding(now))
    }

    @Test
    fun `an unpaid invoice past its due date is overdue`() {
        val invoice = invoice(dueAt = now - 5.days)

        assertEquals(PaymentState.Overdue, invoice.paymentState(now))
        assertEquals(5.days, invoice.overdueBy(now))
        assertEquals(Money.ofMajor(4_500, usd), invoice.outstanding(now))
    }

    @Test
    fun `a draft owes nothing however large its lines`() {
        val draft = invoice(status = InvoiceStatus.Draft, dueAt = now - 90.days)

        assertEquals(PaymentState.Draft, draft.paymentState(now))
        assertEquals(Money.zero(usd), draft.outstanding(now))
        assertFalse(draft.paymentState(now).isOutstanding)
    }

    @Test
    fun `a void invoice owes nothing but keeps its figures for the audit trail`() {
        val voided = invoice(status = InvoiceStatus.Void, dueAt = now - 90.days)

        assertEquals(PaymentState.Void, voided.paymentState(now))
        assertEquals(Money.zero(usd), voided.outstanding(now))
        assertEquals(Money.ofMajor(4_500, usd), voided.total, "the record must survive being voided")
    }

    @Test
    fun `an overpayment shows as a negative balance rather than being hidden`() {
        val invoice = invoice(payments = listOf(payment(5_000)))

        assertEquals(PaymentState.Paid, invoice.paymentState(now))
        assertEquals(Money.ofMajor(-500, usd), invoice.balanceDue)
    }

    @Test
    fun `a retainer and a balance sum to the contract value`() {
        val retainer =
            invoice(
                lines = listOf(LineItem("Retainer", Money.ofMajor(1_500, usd))),
                payments = listOf(payment(1_500)),
            ).copy(kind = InvoiceKind.Retainer)

        val balance = invoice(lines = listOf(LineItem("Balance", Money.ofMajor(3_000, usd))))

        assertEquals(Money.ofMajor(4_500, usd), retainer.total + balance.total)
        assertEquals(Money.ofMajor(3_000, usd), retainer.outstanding(now) + balance.outstanding(now))
    }
}
