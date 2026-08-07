package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightCodbRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightExpenseRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightInvoiceRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightProjectRepository
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.codb.CodbProfile
import com.yellowtrack.platform.core.model.codb.CodbProfileId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.invoice.PaymentId
import com.yellowtrack.platform.core.model.invoice.PaymentMethod
import com.yellowtrack.platform.core.model.invoice.PaymentState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * The write paths the forms drive, exercised against a real database.
 *
 * These cover the behaviour a person would otherwise have to click through to check:
 * that recording a cost moves the pricing floor, and that recording a payment moves the
 * balance and clears the overdue state.
 */
class LedgerWriteFlowTest {
    private val usd = CurrencyCode.USD
    private val clock = AppClock { TEST_NOW }

    private class Harness(
        provider: DatabaseProvider = testDatabaseProvider(),
        clock: AppClock,
    ) {
        val clients = SqlDelightClientRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val projects =
            SqlDelightProjectRepository(
                provider,
                LocalStudioContext(),
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(AcceptingTransport),
            )
        val invoices =
            SqlDelightInvoiceRepository(
                provider,
                LocalStudioContext(),
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(AcceptingTransport),
            )
        val expenses =
            SqlDelightExpenseRepository(
                provider,
                LocalStudioContext(),
                clock,
                Dispatchers.Unconfined,
                RemoteWriter(AcceptingTransport),
            )
        val codb =
            SqlDelightCodbRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined, expenses)
    }

    private fun harness() = Harness(clock = clock)

    private suspend fun Harness.seedBooking(): com.yellowtrack.platform.core.model.project.ProjectId {
        val client = Fixtures.client()
        clients.saveClient(client)
        val project = Fixtures.project(clientId = client.id)
        projects.saveProject(project)
        return project.id
    }

    private fun profile(billableDays: Int = 100) =
        CodbProfile(
            id = CodbProfileId.new(),
            studioId = TEST_STUDIO_ID,
            currency = usd,
            targetAnnualSalary = Money.ofMajor(40_000, usd),
            billableDaysPerYear = billableDays,
            audit = AuditMetadata.createdAt(TEST_NOW),
        )

    @Test
    fun `recording overhead raises the pricing floor`() =
        runTest {
            val harness = harness()
            harness.codb.saveProfile(profile())

            val before = assertNotNull(harness.codb.observeBreakdown(2026).first())
            assertEquals(Money.ofMajor(400, usd), before.costPerBillableDay)

            harness.expenses.saveExpense(
                Expense(
                    id = ExpenseId.new(),
                    studioId = TEST_STUDIO_ID,
                    category = ExpenseCategory.Insurance,
                    description = "Studio insurance",
                    amount = Money.ofMajor(2_000, usd),
                    incurredOn = LocalDate(2026, 3, 1),
                    audit = AuditMetadata.createdAt(TEST_NOW),
                ),
            )

            val after = assertNotNull(harness.codb.observeBreakdown(2026).first())
            assertEquals(Money.ofMajor(420, usd), after.costPerBillableDay)
            assertTrue(after.costPerBillableDay > before.costPerBillableDay)
        }

    @Test
    fun `charging a cost to a job leaves the pricing floor alone`() =
        runTest {
            val harness = harness()
            harness.codb.saveProfile(profile())
            val projectId = harness.seedBooking()

            harness.expenses.saveExpense(
                Expense(
                    id = ExpenseId.new(),
                    studioId = TEST_STUDIO_ID,
                    category = ExpenseCategory.SecondShooter,
                    description = "Second shooter",
                    amount = Money.ofMajor(400, usd),
                    incurredOn = LocalDate(2026, 5, 20),
                    projectId = projectId,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                ),
            )

            assertEquals(
                Money.ofMajor(400, usd),
                assertNotNull(harness.codb.observeBreakdown(2026).first()).costPerBillableDay,
                "a cost already charged to a job must not also be spread across every job",
            )
        }

    @Test
    fun `paying an overdue invoice in full clears it`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedBooking()

            val invoice =
                Invoice(
                    id = InvoiceId.new(),
                    studioId = TEST_STUDIO_ID,
                    projectId = projectId,
                    number = "INV-001",
                    kind = InvoiceKind.Balance,
                    status = InvoiceStatus.Sent,
                    currency = usd,
                    lines = listOf(LineItem("Balance", Money.ofMajor(3_000, usd))),
                    issuedAt = TEST_NOW - 40.days,
                    dueAt = TEST_NOW - 10.days,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                )
            harness.invoices.saveInvoice(invoice)

            assertEquals(
                PaymentState.Overdue,
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
                    .paymentState(TEST_NOW),
            )

            harness.invoices.recordPayment(
                Payment(
                    id = PaymentId.new(),
                    studioId = TEST_STUDIO_ID,
                    invoiceId = invoice.id,
                    amount = Money.ofMajor(3_000, usd),
                    paidAt = TEST_NOW,
                    method = PaymentMethod.BankTransfer,
                    reference = "FT2026-0417",
                    audit = AuditMetadata.createdAt(TEST_NOW),
                ),
            )

            val settled =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(PaymentState.Paid, settled.paymentState(TEST_NOW))
            assertTrue(settled.balanceDue.isZero)
            assertEquals(Money.zero(usd), settled.outstanding(TEST_NOW))
        }

    @Test
    fun `a part payment leaves the invoice overdue for the remainder`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedBooking()

            val invoice =
                Invoice(
                    id = InvoiceId.new(),
                    studioId = TEST_STUDIO_ID,
                    projectId = projectId,
                    number = "INV-002",
                    kind = InvoiceKind.Balance,
                    status = InvoiceStatus.Sent,
                    currency = usd,
                    lines = listOf(LineItem("Balance", Money.ofMajor(3_000, usd))),
                    dueAt = TEST_NOW - 5.days,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                )
            harness.invoices.saveInvoice(invoice)

            harness.invoices.recordPayment(
                Payment(
                    id = PaymentId.new(),
                    studioId = TEST_STUDIO_ID,
                    invoiceId = invoice.id,
                    amount = Money.ofMajor(1_000, usd),
                    paidAt = TEST_NOW,
                    method = PaymentMethod.Card,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                ),
            )

            val stored =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(
                PaymentState.Overdue,
                stored.paymentState(TEST_NOW),
                "part payment does not stop the remainder being late",
            )
            assertEquals(Money.ofMajor(2_000, usd), stored.balanceDue)
        }

    @Test
    fun `two payments settle an invoice between them`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedBooking()

            val invoice =
                Invoice(
                    id = InvoiceId.new(),
                    studioId = TEST_STUDIO_ID,
                    projectId = projectId,
                    number = "INV-003",
                    kind = InvoiceKind.Full,
                    status = InvoiceStatus.Sent,
                    currency = usd,
                    lines = listOf(LineItem("Wedding coverage", Money.ofMajor(4_500, usd))),
                    dueAt = TEST_NOW + 30.days,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                )
            harness.invoices.saveInvoice(invoice)

            listOf(1_500L, 3_000L).forEach { amount ->
                harness.invoices.recordPayment(
                    Payment(
                        id = PaymentId.new(),
                        studioId = TEST_STUDIO_ID,
                        invoiceId = invoice.id,
                        amount = Money.ofMajor(amount, usd),
                        paidAt = TEST_NOW,
                        method = PaymentMethod.BankTransfer,
                        audit = AuditMetadata.createdAt(TEST_NOW),
                    ),
                )
            }

            val stored =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(Money.ofMajor(4_500, usd), stored.amountPaid)
            assertEquals(PaymentState.Paid, stored.paymentState(TEST_NOW))
        }
}
