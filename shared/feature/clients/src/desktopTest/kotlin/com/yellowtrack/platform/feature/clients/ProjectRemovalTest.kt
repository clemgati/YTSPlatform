package com.yellowtrack.platform.feature.clients

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.billing.LineItem
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.InvoiceStatus
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeContractRepository
import com.yellowtrack.platform.core.testing.FakeDeliverableRepository
import com.yellowtrack.platform.core.testing.FakeExpenseRepository
import com.yellowtrack.platform.core.testing.FakeInvoiceRepository
import com.yellowtrack.platform.core.testing.FakePostProductionRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeQuoteRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.testing.TestData
import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.project.ProjectDetailsViewModel
import com.yellowtrack.platform.feature.clients.presentation.project.mapper.projectRemoval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What holds a booking in place.
 *
 * A booking is the opposite shape to a client. A client is held by its bookings and holds
 * nothing itself; a booking is what everything else hangs off — eight kinds of record
 * point at one, and six of them cannot exist without it. Removing a booking and taking its
 * invoices and payments along would delete the record of money that actually changed
 * hands, so nothing cascades and anything at all attached is enough to stop it.
 *
 * The counts are the interesting part rather than the refusal. A studio told only "no" has
 * to guess what is in the way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectRemovalTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a booking with nothing on it can be removed`() {
        assertEquals(Removal.Available, removal())
    }

    @Test
    fun `one shoot day is enough to hold it`() {
        val held = removal(shootDays = 1)

        assertIs<Removal.HeldBy>(held)
        assertEquals(
            "1 shoot day",
            held.summary,
            "singular, because \"1 shoot days\" is the kind of thing that makes a studio " +
                "trust the rest of the screen less",
        )
    }

    @Test
    fun `everything attached is named, not just the first thing found`() {
        val held = removal(invoices = 2, shootDays = 1, postProductionTasks = 3)

        assertIs<Removal.HeldBy>(held)
        assertEquals(
            "2 invoices, 1 shoot day and 3 post-production tasks",
            held.summary,
            "a studio clearing a booking needs the whole list, or it removes the shoot day, " +
                "presses again, and is refused a second time for a different reason",
        )
    }

    @Test
    fun `money is named first`() {
        val held = removal(deliverables = 1, invoices = 1)

        assertIs<Removal.HeldBy>(held)
        assertEquals(
            "1 invoice and 1 deliverable",
            held.summary,
            "the first reason read is the one the decision gets weighed against, and an " +
                "invoice is a heavier reason to stop than a deliverable",
        )
    }

    @Test
    fun `a cost charged to the booking holds it, because it is in the tax figures`() {
        val held = removal(costs = 1)

        assertIs<Removal.HeldBy>(held)
        assertEquals("1 cost", held.summary)
    }

    @Test
    fun `a journey holds it too`() {
        val held = removal(journeys = 2)

        assertIs<Removal.HeldBy>(held)
        assertEquals(
            "2 journeys",
            held.summary,
            "mileage points at a booking the same way a cost does, and a journey left " +
                "pointing at a booking that no longer exists is a deduction that can no " +
                "longer be explained",
        )
    }

    @Test
    fun `every kind of attachment is counted`() {
        val held =
            removal(
                invoices = 1,
                quotes = 1,
                contracts = 1,
                costs = 1,
                journeys = 1,
                shootDays = 1,
                deliverables = 1,
                postProductionTasks = 1,
            )

        assertIs<Removal.HeldBy>(held)
        assertEquals(
            8,
            held.holds.size,
            "a kind of record that points at a booking but is not checked here is a silent " +
                "orphan waiting to happen",
        )
    }

    // -- The guard as the screen actually reaches it -----------------------------------------

    @Test
    fun `a booking with nothing on it is removed`() =
        runTest {
            val projects = FakeProjectRepository(listOf(project))
            val viewModel = viewModel(projects = projects)

            viewModel.deleteProject()

            assertNull(
                projects.observeProject(project.id).first(),
                "a booking opened by mistake has to be able to go",
            )
        }

    @Test
    fun `a booking with an invoice on it is not removed`() =
        runTest {
            val projects = FakeProjectRepository(listOf(project))
            val viewModel =
                viewModel(
                    projects = projects,
                    invoices = FakeInvoiceRepository(listOf(invoice())),
                )

            viewModel.deleteProject()

            assertNotNull(
                projects.observeProject(project.id).first(),
                "the guard cannot live only in the layout: another device may have invoiced " +
                    "this job since the screen was drawn, and removing it would delete the " +
                    "record of money that changed hands",
            )
        }

    @Test
    fun `removing says so, rather than reporting the booking is missing`() =
        runTest {
            val viewModel = viewModel()

            assertFalse(viewModel.uiState.first { it.project is UiState.Success }.removed)

            viewModel.deleteProject()

            val state = viewModel.uiState.first()
            assertTrue(state.removed, "the screen has to know to leave")
            assertTrue(
                state.project !is UiState.Error,
                "gone because it was just removed is not a failure to load it",
            )
        }

    private fun viewModel(
        projects: FakeProjectRepository = FakeProjectRepository(listOf(project)),
        invoices: FakeInvoiceRepository = FakeInvoiceRepository(),
    ) = ProjectDetailsViewModel(
        projectId = project.id,
        projectRepository = projects,
        clientRepository = FakeClientRepository(listOf(client)),
        sessionRepository = FakeSessionRepository(),
        postProductionRepository = FakePostProductionRepository(),
        deliverableRepository = FakeDeliverableRepository(),
        contractRepository = FakeContractRepository(),
        invoiceRepository = invoices,
        quoteRepository = FakeQuoteRepository(),
        expenseRepository = FakeExpenseRepository(),
        studioProfileRepository = FakeStudioProfileRepository(),
        studioContext = LocalStudioContext(),
        clock = clock,
    )

    private val clock = TestAppClock()
    private val client = TestData.couple()

    private val project =
        Project(
            id = ProjectId("project-1"),
            studioId = client.studioId,
            clientId = client.id,
            name = "Okafor — Wedding",
            serviceLine = ServiceLine.Wedding,
            status = ProjectStatus.Booked,
            audit = AuditMetadata.createdAt(clock.now()),
        )

    private fun invoice() =
        Invoice(
            id = InvoiceId("invoice-1"),
            studioId = client.studioId,
            projectId = project.id,
            number = "2026-014",
            kind = InvoiceKind.Balance,
            status = InvoiceStatus.Sent,
            currency = CurrencyCode.GBP,
            lines =
                listOf(
                    LineItem(
                        description = "Coverage",
                        unitPrice = Money(minorUnits = 100_000, currency = CurrencyCode.GBP),
                    ),
                ),
            issuedAt = clock.now(),
            audit = AuditMetadata.createdAt(clock.now()),
        )

    private fun removal(
        invoices: Int = 0,
        quotes: Int = 0,
        contracts: Int = 0,
        costs: Int = 0,
        journeys: Int = 0,
        shootDays: Int = 0,
        deliverables: Int = 0,
        postProductionTasks: Int = 0,
    ) = projectRemoval(
        invoices = invoices,
        quotes = quotes,
        contracts = contracts,
        costs = costs,
        journeys = journeys,
        shootDays = shootDays,
        deliverables = deliverables,
        postProductionTasks = postProductionTasks,
    )
}
