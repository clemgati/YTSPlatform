package com.yellowtrack.platform.core.data

import app.cash.turbine.test
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightCodbRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightExpenseRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightInvoiceRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightLeadRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightProjectRepository
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
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.lead.LeadSource
import com.yellowtrack.platform.core.model.lead.LeadStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class LedgerRepositoryTest {
    private val usd = CurrencyCode.USD
    private val clock = AppClock { TEST_NOW }

    private class Harness(
        provider: DatabaseProvider = testDatabaseProvider(),
        clock: AppClock,
    ) {
        val clients = SqlDelightClientRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val projects = SqlDelightProjectRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val leads = SqlDelightLeadRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val invoices = SqlDelightInvoiceRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val expenses = SqlDelightExpenseRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined)
        val codb =
            SqlDelightCodbRepository(provider, LocalStudioContext(), clock, Dispatchers.Unconfined, expenses)

        /** Ledger rows reference a project, which references a client. */
        suspend fun seedProject(): ProjectId {
            val client = Fixtures.client()
            clients.saveClient(client)
            val project = Fixtures.project(clientId = client.id)
            projects.saveProject(project)
            return project.id
        }
    }

    private fun harness() = Harness(clock = clock)

    private fun lead(
        name: String = "June Wedding Enquiry",
        source: LeadSource = LeadSource.Instagram,
        status: LeadStatus = LeadStatus.New,
        firstResponseAt: kotlin.time.Instant? = null,
        receivedAt: kotlin.time.Instant = TEST_NOW,
    ) = Lead(
        id = LeadId.new(),
        studioId = TEST_STUDIO_ID,
        name = name,
        source = source,
        status = status,
        receivedAt = receivedAt,
        email = "enquiry@example.com",
        firstResponseAt = firstResponseAt,
        budgetLow = Money.ofMajor(3_000, usd),
        budgetHigh = Money.ofMajor(5_000, usd),
        audit = AuditMetadata.createdAt(TEST_NOW),
    )

    private fun invoice(
        projectId: ProjectId,
        number: String = "INV-001",
        kind: InvoiceKind = InvoiceKind.Balance,
        status: InvoiceStatus = InvoiceStatus.Sent,
        amount: Long = 4_500,
        dueAt: kotlin.time.Instant? = TEST_NOW + 30.days,
    ) = Invoice(
        id = InvoiceId.new(),
        studioId = TEST_STUDIO_ID,
        projectId = projectId,
        number = number,
        kind = kind,
        status = status,
        currency = usd,
        lines = listOf(LineItem("Coverage", Money.ofMajor(amount, usd))),
        issuedAt = TEST_NOW,
        dueAt = dueAt,
        audit = AuditMetadata.createdAt(TEST_NOW),
    )

    private fun expense(
        description: String = "Studio insurance",
        amount: Long = 1_200,
        category: ExpenseCategory = ExpenseCategory.Insurance,
        projectId: ProjectId? = null,
        incurredOn: LocalDate = LocalDate(2026, 3, 1),
    ) = Expense(
        id = ExpenseId.new(),
        studioId = TEST_STUDIO_ID,
        category = category,
        description = description,
        amount = Money.ofMajor(amount, usd),
        incurredOn = incurredOn,
        projectId = projectId,
        audit = AuditMetadata.createdAt(TEST_NOW),
    )

    // --- Leads -------------------------------------------------------------------------

    @Test
    fun `stores an enquiry without creating a client account for it`() =
        runTest {
            val harness = harness()

            harness.leads.saveLead(lead(name = "June Wedding Enquiry"))

            assertEquals(
                listOf("June Wedding Enquiry"),
                harness.leads
                    .observeLeads()
                    .first()
                    .map(Lead::name),
            )
            assertTrue(
                harness.clients
                    .observeClients()
                    .first()
                    .isEmpty(),
                "an enquiry is not a client until it books",
            )
        }

    @Test
    fun `an unanswered enquiry appears in the response queue`() =
        runTest {
            val harness = harness()

            harness.leads.saveLead(lead(name = "Unanswered"))
            harness.leads.saveLead(lead(name = "Replied to", firstResponseAt = TEST_NOW + 2.hours))

            assertEquals(
                listOf("Unanswered"),
                harness.leads
                    .observeAwaitingResponse()
                    .first()
                    .map(Lead::name),
            )
        }

    @Test
    fun `recording a reply computes the response time and clears the queue`() =
        runTest {
            val harness = harness()
            val enquiry = lead()
            harness.leads.saveLead(enquiry)

            harness.leads.saveLead(enquiry.copy(firstResponseAt = TEST_NOW + 3.hours, status = LeadStatus.Contacted))

            val stored = assertNotNull(harness.leads.getLead(enquiry.id))
            assertEquals(3.hours, stored.responseTime)
            assertTrue(
                harness.leads
                    .observeAwaitingResponse()
                    .first()
                    .isEmpty(),
            )
        }

    @Test
    fun `won and lost enquiries drop out of the open pipeline`() =
        runTest {
            val harness = harness()

            harness.leads.saveLead(lead(name = "Still deciding", status = LeadStatus.ProposalSent))
            harness.leads.saveLead(lead(name = "Booked", status = LeadStatus.Won))
            harness.leads.saveLead(lead(name = "Went elsewhere", status = LeadStatus.Lost))

            assertEquals(
                listOf("Still deciding"),
                harness.leads
                    .observeOpenLeads()
                    .first()
                    .map(Lead::name),
            )
        }

    @Test
    fun `preserves the budget range and its currency`() =
        runTest {
            val harness = harness()
            val enquiry = lead()
            harness.leads.saveLead(enquiry)

            val stored = assertNotNull(harness.leads.getLead(enquiry.id))
            assertEquals(Money.ofMajor(3_000, usd), stored.budgetLow)
            assertEquals(Money.ofMajor(5_000, usd), stored.budgetHigh)
        }

    // --- Invoices ----------------------------------------------------------------------

    @Test
    fun `an invoice with no payments is awaiting payment`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()

            harness.invoices.saveInvoice(invoice(projectId))

            val stored =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(PaymentState.AwaitingPayment, stored.paymentState(TEST_NOW))
            assertEquals(Money.ofMajor(4_500, usd), stored.balanceDue)
        }

    @Test
    fun `recording a payment updates the balance without a stored flag`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val invoice = invoice(projectId)
            harness.invoices.saveInvoice(invoice)

            harness.invoices.recordPayment(
                Payment(
                    id = PaymentId.new(),
                    studioId = TEST_STUDIO_ID,
                    invoiceId = invoice.id,
                    amount = Money.ofMajor(1_500, usd),
                    paidAt = TEST_NOW,
                    method = PaymentMethod.BankTransfer,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                ),
            )

            val stored = assertNotNull(harness.invoices.getInvoice(invoice.id))
            assertEquals(PaymentState.PartiallyPaid, stored.paymentState(TEST_NOW))
            assertEquals(Money.ofMajor(3_000, usd), stored.balanceDue)
        }

    @Test
    fun `the invoice list reacts the moment a payment is recorded`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()
            val invoice = invoice(projectId)
            harness.invoices.saveInvoice(invoice)

            harness.invoices.observeInvoices().test {
                assertEquals(Money.ofMajor(4_500, usd), awaitItem().single().balanceDue)

                harness.invoices.recordPayment(
                    Payment(
                        id = PaymentId.new(),
                        studioId = TEST_STUDIO_ID,
                        invoiceId = invoice.id,
                        amount = Money.ofMajor(4_500, usd),
                        paidAt = TEST_NOW,
                        method = PaymentMethod.Card,
                        audit = AuditMetadata.createdAt(TEST_NOW),
                    ),
                )

                assertEquals(PaymentState.Paid, awaitItem().single().paymentState(TEST_NOW))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an unpaid invoice past its due date is overdue`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()

            harness.invoices.saveInvoice(invoice(projectId, dueAt = TEST_NOW - 10.days))

            val stored =
                harness.invoices
                    .observeInvoices()
                    .first()
                    .single()
            assertEquals(PaymentState.Overdue, stored.paymentState(TEST_NOW))
            assertEquals(10.days, stored.overdueBy(TEST_NOW))
        }

    @Test
    fun `a retainer and a balance both attach to the same booking`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()

            harness.invoices.saveInvoice(invoice(projectId, "INV-001", InvoiceKind.Retainer, amount = 1_500))
            harness.invoices.saveInvoice(invoice(projectId, "INV-002", InvoiceKind.Balance, amount = 3_000))

            val forProject = harness.invoices.observeInvoicesForProject(projectId).first()
            assertEquals(2, forProject.size)
            assertEquals(
                Money.ofMajor(4_500, usd),
                forProject.fold(Money.zero(usd)) { total, it -> total + it.total },
            )
        }

    @Test
    fun `preserves line items and their tax rates across a round trip`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()

            val withAlbum =
                invoice(projectId).copy(
                    lines =
                        listOf(
                            LineItem("Coverage", Money.ofMajor(4_000, usd)),
                            LineItem("Album", Money.ofMajor(600, usd), taxRateBasisPoints = 825),
                        ),
                )
            harness.invoices.saveInvoice(withAlbum)

            val stored = assertNotNull(harness.invoices.getInvoice(withAlbum.id))
            assertEquals(2, stored.lines.size)
            assertEquals(Money(4_950, usd), stored.tax)
            assertEquals(Money(464_950, usd), stored.total)
        }

    // --- Expenses and cost of doing business -------------------------------------------

    @Test
    fun `separates overhead from job costs`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()

            harness.expenses.saveExpense(expense(description = "Studio insurance", amount = 1_200))
            harness.expenses.saveExpense(
                expense(
                    description = "Second shooter",
                    amount = 400,
                    category = ExpenseCategory.SecondShooter,
                    projectId = projectId,
                ),
            )

            val overhead =
                harness.expenses
                    .observeOverheadBetween(LocalDate(2026, 1, 1), LocalDate(2027, 1, 1))
                    .first()

            assertEquals(listOf("Studio insurance"), overhead.map(Expense::description))
            assertTrue(overhead.all(Expense::isOverhead))

            val jobCosts = harness.expenses.observeExpensesForProject(projectId).first()
            assertEquals(listOf("Second shooter"), jobCosts.map(Expense::description))
        }

    @Test
    fun `overhead is scoped to the requested year`() =
        runTest {
            val harness = harness()

            harness.expenses.saveExpense(expense(description = "This year", incurredOn = LocalDate(2026, 6, 1)))
            harness.expenses.saveExpense(expense(description = "Last year", incurredOn = LocalDate(2025, 6, 1)))

            val thisYear =
                harness.expenses
                    .observeOverheadBetween(LocalDate(2026, 1, 1), LocalDate(2027, 1, 1))
                    .first()

            assertEquals(listOf("This year"), thisYear.map(Expense::description))
        }

    @Test
    fun `the cost of doing business is unavailable until a studio states its targets`() =
        runTest {
            val harness = harness()

            assertNull(
                harness.codb.observeBreakdown(2026).first(),
                "the calculation cannot guess a salary target or billable days",
            )
        }

    @Test
    fun `recorded overhead feeds the cost of doing business`() =
        runTest {
            val harness = harness()

            harness.codb.saveProfile(
                CodbProfile(
                    id = CodbProfileId.new(),
                    studioId = TEST_STUDIO_ID,
                    currency = usd,
                    targetAnnualSalary = Money.ofMajor(40_000, usd),
                    billableDaysPerYear = 100,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                ),
            )

            harness.expenses.saveExpense(expense(description = "Insurance", amount = 1_200))
            harness.expenses.saveExpense(
                expense(description = "Software", amount = 800, category = ExpenseCategory.Software),
            )

            val breakdown = assertNotNull(harness.codb.observeBreakdown(2026).first())

            assertEquals(Money.ofMajor(2_000, usd), breakdown.annualOverhead)
            assertEquals(Money.ofMajor(42_000, usd), breakdown.totalAnnualRequirement)
            assertEquals(Money.ofMajor(420, usd), breakdown.costPerBillableDay)
        }

    @Test
    fun `a job cost is excluded from overhead so it is not counted twice`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()

            harness.codb.saveProfile(
                CodbProfile(
                    id = CodbProfileId.new(),
                    studioId = TEST_STUDIO_ID,
                    currency = usd,
                    targetAnnualSalary = Money.ofMajor(40_000, usd),
                    billableDaysPerYear = 100,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                ),
            )

            harness.expenses.saveExpense(expense(description = "Insurance", amount = 1_000))
            harness.expenses.saveExpense(
                expense(
                    description = "Second shooter",
                    amount = 400,
                    category = ExpenseCategory.SecondShooter,
                    projectId = projectId,
                ),
            )

            val breakdown = assertNotNull(harness.codb.observeBreakdown(2026).first())

            assertEquals(
                Money.ofMajor(1_000, usd),
                breakdown.annualOverhead,
                "a cost already charged to a job must not also be spread across every job",
            )
        }

    /**
     * A worked example with real figures, end to end through the database.
     *
     * A photographer wanting 45,000 in the hand, able to sell 90 days a year, taxed at
     * 28%, carrying 2,760 of overhead, cannot sell a day for less than 725.12 — and a
     * four-day wedding for less than 2,900.48. Almost nobody guesses that correctly.
     */
    @Test
    fun `a realistic studio arrives at a defensible day rate`() =
        runTest {
            val harness = harness()

            harness.codb.saveProfile(
                CodbProfile(
                    id = CodbProfileId.new(),
                    studioId = TEST_STUDIO_ID,
                    currency = usd,
                    targetAnnualSalary = Money.ofMajor(45_000, usd),
                    billableDaysPerYear = 90,
                    taxRateBasisPoints = 2_800,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                ),
            )

            harness.expenses.saveExpense(expense(description = "Insurance", amount = 1_800))
            harness.expenses.saveExpense(
                expense(description = "Software", amount = 960, category = ExpenseCategory.Software),
            )

            val breakdown = assertNotNull(harness.codb.observeBreakdown(2026).first())

            // 45,000 after 28% tax requires 62,500 earned.
            assertEquals(Money.ofMajor(62_500 - 45_000, usd), breakdown.taxAllowance)
            assertEquals(Money.ofMajor(2_760, usd), breakdown.annualOverhead)
            assertEquals(Money.ofMajor(65_260, usd), breakdown.totalAnnualRequirement)

            // 65,260 across 90 sellable days, rounded up.
            assertEquals(Money(72_512, usd), breakdown.costPerBillableDay)
            assertEquals(Money(290_048, usd), breakdown.minimumPriceFor(daysConsumed = 4.0))

            // A wedding sold at 2,500 for four days of work loses money.
            assertTrue(breakdown.assess(Money.ofMajor(2_500, usd), daysConsumed = 4.0).isBelowCost)
        }

    @Test
    fun `the profile is one row per studio and saving again replaces it`() =
        runTest {
            val harness = harness()
            val profile =
                CodbProfile(
                    id = CodbProfileId.new(),
                    studioId = TEST_STUDIO_ID,
                    currency = usd,
                    targetAnnualSalary = Money.ofMajor(40_000, usd),
                    billableDaysPerYear = 100,
                    audit = AuditMetadata.createdAt(TEST_NOW),
                )

            harness.codb.saveProfile(profile)
            harness.codb.saveProfile(profile.copy(billableDaysPerYear = 80))

            assertEquals(80, assertNotNull(harness.codb.getProfile()).billableDaysPerYear)
        }

    @Test
    fun `mileage records its rate and computes the deduction`() =
        runTest {
            val harness = harness()
            val projectId = harness.seedProject()

            harness.expenses.saveMileage(
                com.yellowtrack.platform.core.model.expense.Mileage(
                    id =
                        com.yellowtrack.platform.core.model.expense.MileageId
                            .new(),
                    studioId = TEST_STUDIO_ID,
                    travelledOn = LocalDate(2026, 6, 13),
                    distance = 120.5,
                    unit = com.yellowtrack.platform.core.model.expense.DistanceUnit.Miles,
                    ratePerUnit = Money(67, usd),
                    projectId = projectId,
                    purpose = "Venue scout",
                    audit = AuditMetadata.createdAt(TEST_NOW),
                ),
            )

            val stored =
                harness.expenses
                    .observeMileage()
                    .first()
                    .single()

            assertEquals(120.5, stored.distance)
            // 120.5 miles at 0.67 is 80.735, rounded to 80.74.
            assertEquals(Money(8_074, usd), stored.deductibleAmount)
        }
}
