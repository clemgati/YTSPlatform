package com.yellowtrack.platform.feature.clients

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.testing.TestData
import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsUiState
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsViewModel
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
 * Taking back a client entered by mistake.
 *
 * Since 0.1.0 an account could be created and edited but never removed, while a lighting
 * recipe could be deleted outright — so the first thing a studio types into this
 * application was the one thing it could not undo. A wrong name can be edited; a duplicate
 * account, or one for an enquiry that turned out to be a competitor, cannot be edited into
 * not existing.
 *
 * The rule guarded here is that bookings hold an account in place. It is safe because
 * `Session.projectId` and `Invoice.projectId` cannot be null: shoot days, invoices and
 * payments hang off a booking, never off the client directly. So no bookings really does
 * mean nothing behind it, and the guard is the difference between removing an account and
 * silently destroying a year of accounts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClientRemovalTest {
    private val clock = TestAppClock()
    private val client = TestData.couple()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an account with nothing booked can be removed`() =
        runTest {
            val clients = FakeClientRepository(listOf(client))
            val viewModel = viewModel(clients = clients)

            assertEquals(Removal.Available, model(viewModel.uiState.first()).removal)

            viewModel.deleteClient()

            assertNull(
                clients.observeClient(client.id).first(),
                "a client entered by mistake has to be able to go",
            )
        }

    @Test
    fun `an account with a booking is held, and says how many`() =
        runTest {
            val viewModel =
                viewModel(
                    projects = FakeProjectRepository(listOf(project("p1"), project("p2"))),
                )

            val removal = model(viewModel.uiState.first()).removal

            assertIs<Removal.HeldBy>(removal, "bookings carry the money; they hold the account")
            assertEquals(
                "2 bookings",
                removal.summary,
                "the count is what makes the message concrete enough to act on",
            )
        }

    @Test
    fun `pressing remove on a held account does nothing`() =
        runTest {
            val clients = FakeClientRepository(listOf(client))
            val viewModel =
                viewModel(
                    clients = clients,
                    projects = FakeProjectRepository(listOf(project("p1"))),
                )

            viewModel.deleteClient()

            assertNotNull(
                clients.observeClient(client.id).first(),
                "the guard cannot live only in the layout: another device may have opened a " +
                    "booking since this screen was drawn, and the press would orphan it",
            )
        }

    @Test
    fun `a cancelled booking still holds the account`() =
        runTest {
            val clients = FakeClientRepository(listOf(client))
            val viewModel =
                viewModel(
                    clients = clients,
                    projects = FakeProjectRepository(listOf(project("p1", ProjectStatus.Cancelled))),
                )

            assertIs<Removal.HeldBy>(
                model(viewModel.uiState.first()).removal,
                "a booking that fell through is still a record with costs and possibly a " +
                    "deposit against it; cancelled is not deleted",
            )

            viewModel.deleteClient()

            assertNotNull(clients.observeClient(client.id).first())
        }

    @Test
    fun `removing says so, rather than reporting the account is missing`() =
        runTest {
            val viewModel = viewModel()

            assertFalse(viewModel.uiState.first().removed)

            viewModel.deleteClient()

            val state = viewModel.uiState.first()
            assertTrue(state.removed, "the screen has to know to leave; nothing here exists any more")
            assertTrue(
                state.client !is UiState.Error,
                "the client is gone because it was just removed, which is not a failure to " +
                    "load one — an error here would read as a fault in the application",
            )
        }

    // -- Fixtures ----------------------------------------------------------------------------

    private fun viewModel(
        projects: FakeProjectRepository = FakeProjectRepository(),
        clients: FakeClientRepository = FakeClientRepository(listOf(client)),
    ) = ClientDetailsViewModel(
        clientId = client.id,
        clientRepository = clients,
        projectRepository = projects,
        sessionRepository = FakeSessionRepository(),
        studioProfileRepository = FakeStudioProfileRepository(),
        studioContext = LocalStudioContext(),
        clock = clock,
    )

    private fun project(
        id: String,
        status: ProjectStatus = ProjectStatus.Booked,
    ) = Project(
        id = ProjectId(id),
        studioId = client.studioId,
        clientId = client.id,
        name = "Booking $id",
        serviceLine = ServiceLine.Wedding,
        status = status,
        audit = AuditMetadata.createdAt(clock.now()),
    )

    private fun model(state: ClientDetailsUiState) = (state.client as UiState.Success).data
}
