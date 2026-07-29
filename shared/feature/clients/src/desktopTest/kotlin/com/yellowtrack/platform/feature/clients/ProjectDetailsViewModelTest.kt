package com.yellowtrack.platform.feature.clients

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.post.PostTaskKind
import com.yellowtrack.platform.core.model.post.PostTaskStatus
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeContractRepository
import com.yellowtrack.platform.core.testing.FakeDeliverableRepository
import com.yellowtrack.platform.core.testing.FakePostProductionRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.testing.TestData
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
import com.yellowtrack.platform.feature.clients.presentation.project.ProjectDetailsViewModel
import com.yellowtrack.platform.feature.clients.presentation.project.model.NewPostTask
import com.yellowtrack.platform.feature.clients.presentation.project.model.ProjectDetailsModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * One booking, and the post-production hours that eventually tell the pricing floor how
 * long this studio's work really takes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailsViewModelTest {
    private val clock = TestAppClock()
    private val client = TestData.couple()
    private val project = TestData.project(clientId = client.id)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private lateinit var tasks: FakePostProductionRepository
    private lateinit var projects: FakeProjectRepository
    private lateinit var deliverables: FakeDeliverableRepository

    private fun viewModel(): ProjectDetailsViewModel {
        tasks = FakePostProductionRepository()
        projects = FakeProjectRepository(listOf(project))
        deliverables = FakeDeliverableRepository()

        return ProjectDetailsViewModel(
            projectId = project.id,
            projectRepository = projects,
            clientRepository = FakeClientRepository(listOf(client)),
            sessionRepository = FakeSessionRepository(),
            postProductionRepository = tasks,
            deliverableRepository = deliverables,
            contractRepository = FakeContractRepository(),
            studioContext = LocalStudioContext(),
            clock = clock,
        )
    }

    private suspend fun ProjectDetailsViewModel.model(): ProjectDetailsModel {
        val state = uiState.first { it.project is UiState.Success }
        return (state.project as UiState.Success).data
    }

    // --- Recording the work ------------------------------------------------------------

    @Test
    fun `work is added with what it is expected to take, and starts unfinished`() =
        runTest {
            val viewModel = viewModel()

            viewModel.addTask(NewPostTask("Cull the wedding day", PostTaskKind.Cull, "4"))

            val task =
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
            assertEquals("Cull the wedding day", task.name)
            assertEquals("4h estimated", task.estimatedLabel?.let { "$it estimated" })
            assertEquals(PostTaskStatus.ToDo, task.status)
            assertNull(task.actualLabel, "nothing has been spent on it yet")
        }

    @Test
    fun `finishing work records what it really took`() =
        runTest {
            val viewModel = viewModel()
            viewModel.addTask(NewPostTask("Cull", PostTaskKind.Cull, "4"))
            val id =
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
                    .id

            viewModel.completeTask(id, "6.5")

            val stored = assertNotNull(tasks.getTask(id))
            assertEquals(6.5, stored.actualHours)
            assertEquals(PostTaskStatus.Done, stored.status)
            assertNotNull(stored.completedAt)
            assertTrue(stored.isMeasured, "this is the row the pricing floor can learn from")
        }

    @Test
    fun `work cannot be finished without saying how long it took`() =
        runTest {
            val viewModel = viewModel()
            viewModel.addTask(NewPostTask("Cull", PostTaskKind.Cull, "4"))
            val id =
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
                    .id

            viewModel.completeTask(id, "")

            assertEquals(
                PostTaskStatus.ToDo,
                assertNotNull(tasks.getTask(id)).status,
                "a task closed without hours tells the pricing floor nothing",
            )
        }

    @Test
    fun `an overrun is reported once the work is finished, and not before`() =
        runTest {
            val viewModel = viewModel()
            viewModel.addTask(NewPostTask("Cull", PostTaskKind.Cull, "4"))
            val id =
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
                    .id

            assertNull(
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
                    .overrunLabel,
                "a task half done has not overrun; it is simply not finished",
            )

            viewModel.completeTask(id, "6.5")

            val task =
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
            assertEquals("2.5h over", task.overrunLabel)
            assertTrue(task.isOverrun)
        }

    @Test
    fun `coming in under the estimate is reported as under, not as an overrun`() =
        runTest {
            val viewModel = viewModel()
            viewModel.addTask(NewPostTask("Cull", PostTaskKind.Cull, "6"))
            val id =
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
                    .id

            viewModel.completeTask(id, "4")

            val task =
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
            assertEquals("2h under", task.overrunLabel)
            assertTrue(!task.isOverrun)
        }

    @Test
    fun `a few minutes either way is not worth calling an overrun`() =
        runTest {
            val viewModel = viewModel()
            viewModel.addTask(NewPostTask("Cull", PostTaskKind.Cull, "4"))
            val id =
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
                    .id

            viewModel.completeTask(id, "4.1")

            assertNull(
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
                    .overrunLabel,
                "flagging six minutes would teach the studio to stop reading the figure",
            )
        }

    @Test
    fun `reopening work clears what it claimed to have taken`() =
        runTest {
            val viewModel = viewModel()
            viewModel.addTask(NewPostTask("Cull", PostTaskKind.Cull, "4"))
            val id =
                viewModel
                    .model()
                    .postProduction.tasks
                    .single()
                    .id
            viewModel.completeTask(id, "6.5")

            viewModel.reopenTask(id)

            val stored = assertNotNull(tasks.getTask(id))
            assertNull(stored.actualHours, "work back on the list has not taken any time yet")
            assertNull(stored.completedAt)
            assertTrue(!stored.isMeasured)
        }

    @Test
    fun `the booking totals what was estimated against what was spent`() =
        runTest {
            val viewModel = viewModel()
            viewModel.addTask(NewPostTask("Cull", PostTaskKind.Cull, "4"))
            viewModel.addTask(NewPostTask("Edit", PostTaskKind.Edit, "8"))
            val cull =
                viewModel
                    .model()
                    .postProduction.tasks
                    .first { it.name == "Cull" }

            viewModel.completeTask(cull.id, "6")

            val summary = viewModel.model().postProduction
            assertEquals(12.0, summary.estimatedHours, "both estimates count, finished or not")
            assertEquals(6.0, summary.actualHours, "only what has actually been spent")
            assertEquals(1, summary.remaining)
        }

    @Test
    fun `unfinished work sorts before work that is done`() =
        runTest {
            val viewModel = viewModel()
            viewModel.addTask(NewPostTask("Cull", PostTaskKind.Cull, "4"))
            clock.advanceBy(1.days)
            viewModel.addTask(NewPostTask("Edit", PostTaskKind.Edit, "8"))
            val cull =
                viewModel
                    .model()
                    .postProduction.tasks
                    .first { it.name == "Cull" }

            viewModel.completeTask(cull.id, "6")

            assertEquals(
                listOf("Edit", "Cull"),
                viewModel
                    .model()
                    .postProduction.tasks
                    .map { it.name },
                "what is still owed on this booking is what the page is opened to see",
            )
        }

    @Test
    fun `work with no name is not recorded`() =
        runTest {
            val viewModel = viewModel()

            viewModel.addTask(NewPostTask("  ", PostTaskKind.Edit, "4"))

            assertTrue(
                viewModel
                    .model()
                    .postProduction.tasks
                    .isEmpty(),
            )
        }

    // --- Correcting the booking --------------------------------------------------------

    @Test
    fun `moving a booking to Booked records when the date was taken`() =
        runTest {
            val viewModel = viewModel()
            clock.advanceBy(9.days)

            viewModel.updateProject(
                NewProject(
                    name = project.name,
                    serviceLine = ServiceLine.Wedding,
                    status = ProjectStatus.Booked,
                    contractValue = "",
                    notes = "",
                ),
            )

            val stored =
                projects
                    .observeProjects()
                    .first()
                    .single()
            assertEquals(ProjectStatus.Booked, stored.status)
            assertEquals(TestAppClock.DEFAULT_NOW + 9.days, stored.bookedAt)
        }

    @Test
    fun `a cancelled booking keeps the date it was booked on`() =
        runTest {
            val viewModel = viewModel()

            viewModel.updateProject(
                NewProject(project.name, ServiceLine.Wedding, ProjectStatus.Booked, "", ""),
            )
            val bookedAt =
                projects
                    .observeProjects()
                    .first()
                    .single()
                    .bookedAt

            clock.advanceBy(20.days)
            viewModel.updateProject(
                NewProject(project.name, ServiceLine.Wedding, ProjectStatus.Cancelled, "", ""),
            )

            assertEquals(
                bookedAt,
                projects
                    .observeProjects()
                    .first()
                    .single()
                    .bookedAt,
                "a cancellation fee is measured against the date the job was booked",
            )
        }
}
