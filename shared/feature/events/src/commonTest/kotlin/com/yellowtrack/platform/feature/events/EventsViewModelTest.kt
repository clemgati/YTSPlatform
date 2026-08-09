package com.yellowtrack.platform.feature.events

import com.yellowtrack.platform.core.data.event.EventActionFailed
import com.yellowtrack.platform.core.data.event.EventsApi
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.StationSummary
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.events.presentation.EventsContent
import com.yellowtrack.platform.feature.events.presentation.EventsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
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

/**
 * The screen a photographer looks at while a queue of people waits.
 *
 * Two properties matter more than everything else here, and both are about what happens when
 * something goes wrong in a room rather than at a desk: a refusal must leave the screen
 * usable, and a second tap must not open a second station.
 */
class EventsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun before() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun after() {
        Dispatchers.resetMain()
    }

    @Test
    fun `events load when the screen opens`() =
        runTest(dispatcher) {
            val api = FakeApi(events = listOf(summary("event-1", "Headshot day", openStations = 1)))
            val viewModel = EventsViewModel(api)

            testScheduler.advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(1, content.events.size)
            assertEquals("Headshot day", content.events.single().name)
            assertEquals(1, content.events.single().openStations)
        }

    @Test
    fun `a studio with no events sees an empty screen rather than an error`() =
        runTest(dispatcher) {
            val viewModel = EventsViewModel(FakeApi())

            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.content is UiState.Empty)
        }

    @Test
    fun `opening an event shows its stations`() =
        runTest(dispatcher) {
            val api =
                FakeApi(
                    events = listOf(summary("event-1", "Headshot day")),
                    stations = mapOf("event-1" to listOf(station("station-1", "Bay 1", "Camera A"))),
                )
            val viewModel = EventsViewModel(api)
            testScheduler.advanceUntilIdle()

            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            val open = assertNotNull(viewModel.content().open, "the event should be open")
            assertEquals("Headshot day", open.name)
            assertEquals(listOf("Camera A"), open.stations.map { it.sourceKey })
            assertEquals(setOf("Camera A"), open.claimedSources)
        }

    /** A station closed is a source freed, and the form has to know it. */
    @Test
    fun `a closed station does not claim its source`() =
        runTest(dispatcher) {
            val api =
                FakeApi(
                    events = listOf(summary("event-1", "Headshot day")),
                    stations = mapOf("event-1" to listOf(station("station-1", "Bay 1", "Camera A", closedAt = 99))),
                )
            val viewModel = EventsViewModel(api)
            testScheduler.advanceUntilIdle()

            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            assertTrue(
                viewModel
                    .content()
                    .open!!
                    .claimedSources
                    .isEmpty(),
            )
        }

    // -- A refusal must not take the screen away ------------------------------------------

    /**
     * The property this screen turns on.
     *
     * "A station is already open on Camera A" is only useful next to the list that would let
     * somebody close it. Replacing the screen with an error removes the remedy along with the
     * problem, and leaves a photographer with a queue and a dead screen.
     */
    @Test
    fun `a refused station leaves the list on screen`() =
        runTest(dispatcher) {
            val api =
                FakeApi(
                    events = listOf(summary("event-1", "Headshot day")),
                    stations = mapOf("event-1" to listOf(station("station-1", "Bay 1", "Camera A"))),
                )
            val viewModel = EventsViewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            api.openStationFails =
                EventActionFailed("A station is already open on Camera A. Close it before opening another.")
            viewModel.openStation("event-1", "Bay 2", "Camera A")
            testScheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.content is UiState.Success, "the screen was replaced by an error: ${state.content}")
            assertTrue("Camera A" in state.problem.orEmpty(), "the camera was not named: ${state.problem}")
            assertEquals(
                1,
                viewModel
                    .content()
                    .open!!
                    .stations.size,
                "the station that would have to be closed disappeared from the screen",
            )
        }

    /** And a failing refresh mid-event leaves what is already there. */
    @Test
    fun `a failed refresh during an event does not clear the screen`() =
        runTest(dispatcher) {
            val api = FakeApi(events = listOf(summary("event-1", "Headshot day")))
            val viewModel = EventsViewModel(api)
            testScheduler.advanceUntilIdle()

            api.eventsFails = EventActionFailed("Could not reach the server.")
            viewModel.refresh()
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.content is UiState.Success, "the event list was thrown away")
            assertNotNull(viewModel.uiState.value.problem)
        }

    /** With nothing on screen there is nothing to preserve, so it is an error properly. */
    @Test
    fun `a failure with nothing loaded is an error`() =
        runTest(dispatcher) {
            val api = FakeApi().apply { eventsFails = EventActionFailed("Could not reach the server.") }
            val viewModel = EventsViewModel(api)

            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.content is UiState.Error)
        }

    @Test
    fun `a problem can be dismissed`() =
        runTest(dispatcher) {
            val viewModel = EventsViewModel(FakeApi())
            testScheduler.advanceUntilIdle()

            viewModel.createEvent("  ")
            assertNotNull(viewModel.uiState.value.problem)

            viewModel.dismissProblem()
            assertNull(viewModel.uiState.value.problem)
        }

    // -- One action at a time ---------------------------------------------------------------

    /**
     * Two taps a second apart on a venue's wifi.
     *
     * Without this the second request is already on its way when the first succeeds, and the
     * source ends up with two stations racing for it — the loser being a 409 the photographer
     * reads as the application being broken.
     */
    @Test
    fun `a second tap while one is in flight does nothing`() =
        runTest(dispatcher) {
            val api =
                FakeApi(
                    events = listOf(summary("event-1", "Headshot day")),
                    stations = mapOf("event-1" to emptyList()),
                )
            val viewModel = EventsViewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            viewModel.openStation("event-1", "Bay 1", "Camera A")
            viewModel.openStation("event-1", "Bay 1", "Camera A")
            testScheduler.advanceUntilIdle()

            assertEquals(1, api.stationsOpened, "the same station was opened twice")
        }

    /**
     * And the block has to lift afterwards.
     *
     * Left set, one failed request would leave every button dead for the rest of the event
     * with nothing on screen explaining why.
     */
    @Test
    fun `a failed action does not leave the screen stuck`() =
        runTest(dispatcher) {
            val api =
                FakeApi(
                    events = listOf(summary("event-1", "Headshot day")),
                    stations = mapOf("event-1" to emptyList()),
                )
            val viewModel = EventsViewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            api.openStationFails = EventActionFailed("Could not reach the server.")
            viewModel.openStation("event-1", "Bay 1", "Camera A")
            testScheduler.advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isBusy, "the screen stayed busy after a failure")

            api.openStationFails = null
            viewModel.openStation("event-1", "Bay 1", "Camera A")
            testScheduler.advanceUntilIdle()

            assertEquals(1, api.stationsOpened, "the retry after a failure did nothing")
        }

    // -- Refusing before the server has to ----------------------------------------------------

    @Test
    fun `an event with no name is refused without a request`() =
        runTest(dispatcher) {
            val api = FakeApi()
            val viewModel = EventsViewModel(api)
            testScheduler.advanceUntilIdle()

            viewModel.createEvent("   ")
            testScheduler.advanceUntilIdle()

            assertEquals(0, api.eventsCreated)
            assertNotNull(viewModel.uiState.value.problem)
        }

    @Test
    fun `a station with no folder is refused without a request`() =
        runTest(dispatcher) {
            val api =
                FakeApi(
                    events = listOf(summary("event-1", "Headshot day")),
                    stations = mapOf("event-1" to emptyList()),
                )
            val viewModel = EventsViewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            viewModel.openStation("event-1", "Bay 1", "  ")
            testScheduler.advanceUntilIdle()

            assertEquals(0, api.stationsOpened)
            assertNotNull(viewModel.uiState.value.problem)
        }

    @Test
    fun `closing a station refreshes the list`() =
        runTest(dispatcher) {
            val api =
                FakeApi(
                    events = listOf(summary("event-1", "Headshot day")),
                    stations = mapOf("event-1" to listOf(station("station-1", "Bay 1", "Camera A"))),
                )
            val viewModel = EventsViewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            viewModel.closeStation("event-1", "station-1")
            testScheduler.advanceUntilIdle()

            assertTrue(
                viewModel
                    .content()
                    .open!!
                    .stations
                    .none { it.isOpen },
                "the station still reads as open",
            )
        }

    // -- Fixtures ------------------------------------------------------------------------------

    private fun EventsViewModel.content(): EventsContent =
        (uiState.value.content as UiState.Success<EventsContent>).data

    private fun summary(
        id: String,
        name: String,
        openStations: Int = 0,
    ) = EventSummary(id = id, name = name, startsAt = null, openStations = openStations, photographs = 0)

    private fun station(
        id: String,
        name: String,
        sourceKey: String,
        closedAt: Long? = null,
    ) = StationSummary(id = id, name = name, sourceKey = sourceKey, openedAt = 1_000, closedAt = closedAt)

    private class FakeApi(
        private val events: List<EventSummary> = emptyList(),
        stations: Map<String, List<StationSummary>> = emptyMap(),
    ) : EventsApi {
        private val stationsByEvent = stations.mapValues { it.value.toMutableList() }.toMutableMap()

        var eventsFails: Throwable? = null
        var openStationFails: Throwable? = null
        var stationsOpened = 0
        var eventsCreated = 0

        override suspend fun events(): List<EventSummary> {
            eventsFails?.let { throw it }

            return events
        }

        override suspend fun createEvent(
            name: String,
            startsAt: Long?,
        ): String {
            eventsCreated++

            return "event-new"
        }

        override suspend fun stations(eventId: String): List<StationSummary> = stationsByEvent[eventId].orEmpty()

        override suspend fun openStation(
            eventId: String,
            name: String,
            sourceKey: String,
        ): String {
            openStationFails?.let { throw it }
            stationsOpened++
            val id = "station-$stationsOpened"
            stationsByEvent.getOrPut(eventId) { mutableListOf() } +=
                StationSummary(id, name, sourceKey, openedAt = 2_000, closedAt = null)

            return id
        }

        override suspend fun closeStation(
            eventId: String,
            stationId: String,
        ) {
            val list = stationsByEvent[eventId] ?: return
            val index = list.indexOfFirst { it.id == stationId }
            if (index >= 0) list[index] = list[index].copy(closedAt = 3_000)
        }
    }
}
