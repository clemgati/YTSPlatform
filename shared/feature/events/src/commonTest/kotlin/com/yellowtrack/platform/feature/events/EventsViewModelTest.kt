package com.yellowtrack.platform.feature.events

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.event.ChosenFolder
import com.yellowtrack.platform.core.data.event.EventActionFailed
import com.yellowtrack.platform.core.data.event.EventsApi
import com.yellowtrack.platform.core.data.event.IngestPlatform
import com.yellowtrack.platform.core.data.event.IngestService
import com.yellowtrack.platform.core.data.event.PhotographUploader
import com.yellowtrack.platform.core.data.event.UploadLog
import com.yellowtrack.platform.core.data.event.UploadOutcome
import com.yellowtrack.platform.core.data.event.WatchedFile
import com.yellowtrack.platform.core.data.event.WatchedFolder
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.StationSummary
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.events.presentation.EventsContent
import com.yellowtrack.platform.feature.events.presentation.EventsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

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
            val viewModel = viewModel(api)

            testScheduler.advanceUntilIdle()

            val content = viewModel.content()
            assertEquals(1, content.events.size)
            assertEquals("Headshot day", content.events.single().name)
            assertEquals(1, content.events.single().openStations)
        }

    @Test
    fun `a studio with no events sees an empty screen rather than an error`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeApi())

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
            val viewModel = viewModel(api)
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
            val viewModel = viewModel(api)
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
            val viewModel = viewModel(api)
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
            val viewModel = viewModel(api)
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
            val viewModel = viewModel(api)

            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.content is UiState.Error)
        }

    @Test
    fun `a problem can be dismissed`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeApi())
            testScheduler.advanceUntilIdle()

            // `uiState` is derived from two flows now, so a synchronous update to the
            // screen's own state reaches it on the next tick rather than immediately.
            viewModel.createEvent("  ")
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.problem)

            viewModel.dismissProblem()
            testScheduler.advanceUntilIdle()
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
            val viewModel = viewModel(api)
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
            val viewModel = viewModel(api)
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
            val viewModel = viewModel(api)
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
            val viewModel = viewModel(api)
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
            val viewModel = viewModel(api)
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

    // -- Watching a folder -------------------------------------------------------------------

    @Test
    fun `choosing a folder begins watching it`() =
        runTest(dispatcher) {
            val platform = FakeIngestPlatform()
            val api = openedEvent()
            val viewModel = viewModel(api, platform)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            viewModel.watchFolder("event-1", "Camera A")
            testScheduler.advanceUntilIdle()

            val status = assertNotNull(viewModel.uiState.value.ingest["Camera A"], "nothing is being watched")
            assertEquals("Camera A", status.folderName)
        }

    /**
     * Cancelling the chooser is not a problem, and must not be reported as one.
     *
     * Somebody who opened the dialog and thought better of it has encountered nothing. Saying
     * so would train them to ignore the place real problems appear.
     */
    @Test
    fun `cancelling the folder chooser says nothing`() =
        runTest(dispatcher) {
            val platform = FakeIngestPlatform(chosen = null)
            val viewModel = viewModel(openedEvent(), platform)
            testScheduler.advanceUntilIdle()

            viewModel.watchFolder("event-1", "Camera A")
            testScheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.problem, "cancelling was reported as a problem")
            assertTrue(
                viewModel.uiState.value.ingest
                    .isEmpty(),
            )
        }

    /**
     * The link that matters most on this screen.
     *
     * A source whose station has closed still has a folder full of files. A watch left
     * running keeps sending them, and with no slot open they route to the event's gallery —
     * so a sitting's leftovers quietly become public photographs.
     */
    @Test
    fun `closing a station stops watching its folder`() =
        runTest(dispatcher) {
            val platform = FakeIngestPlatform()
            val viewModel = viewModel(openedEvent(), platform)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            viewModel.watchFolder("event-1", "Camera A")
            testScheduler.advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.ingest["Camera A"])

            viewModel.closeStation("event-1", "station-1")
            testScheduler.advanceUntilIdle()

            // The record stays — a photographer still wants to see what it sent — but the
            // watch itself must have stopped.
            assertNotNull(viewModel.uiState.value.ingest["Camera A"], "the record should survive closing")
            assertFalse(
                watching(viewModel, "Camera A"),
                "the folder is still being watched after its station closed",
            )
        }

    @Test
    fun `stopping a watch leaves the station open`() =
        runTest(dispatcher) {
            val platform = FakeIngestPlatform()
            val viewModel = viewModel(openedEvent(), platform)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.watchFolder("event-1", "Camera A")
            testScheduler.advanceUntilIdle()

            viewModel.stopWatching("Camera A")
            testScheduler.advanceUntilIdle()

            assertTrue(
                viewModel
                    .content()
                    .open!!
                    .stations
                    .single()
                    .isOpen,
                "stopping ingest closed the station as well",
            )
        }

    /** A platform with no capture folder does not get offered the control. */
    @Test
    fun `a platform that cannot watch folders says so`() =
        runTest(dispatcher) {
            val viewModel = viewModel(openedEvent(), FakeIngestPlatform(canWatchFolders = false))
            testScheduler.advanceUntilIdle()

            assertFalse(viewModel.uiState.value.canWatchFolders)
        }

    // -- Fixtures ------------------------------------------------------------------------------

    /**
     * A view model with a real [IngestService] behind a fake platform.
     *
     * Real rather than mocked because the link being tested — closing a station stops its
     * watch — is precisely the join between the two, and a stubbed service would assert that
     * the call was made rather than that ingest actually stopped.
     */
    private fun TestScope.viewModel(
        api: EventsApi,
        platform: IngestPlatform = FakeIngestPlatform(),
    ) = EventsViewModel(
        api = api,
        ingest =
            IngestService(
                platform = platform,
                uploader = NeverUploader,
                scope = backgroundScope,
                clock = AppClock { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
            ),
        platform = platform,
    ).also { viewModel ->
        // `uiState` is shared `WhileSubscribed`, so with nobody collecting it never leaves its
        // initial value and every assertion below would read `Loading`. Compose subscribes for
        // as long as the screen is on show; this is that, for as long as the test runs.
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    private class FakeIngestPlatform(
        override val canWatchFolders: Boolean = true,
        var chosen: ChosenFolder? = ChosenFolder("/Volumes/Capture/Camera A", "Camera A"),
        private val failToChoose: Throwable? = null,
    ) : IngestPlatform {
        var timesAsked = 0

        override suspend fun chooseFolder(): ChosenFolder? {
            timesAsked++
            failToChoose?.let { throw it }

            return chosen
        }

        override fun folderAt(path: String): WatchedFolder =
            object : WatchedFolder {
                override fun list(): List<WatchedFile> = emptyList()

                override fun read(path: String): ByteArray = ByteArray(0)
            }

        override fun logFor(
            folderPath: String,
            eventId: String,
            sourceKey: String,
        ): UploadLog =
            object : UploadLog {
                override suspend fun handled(): Set<String> = emptySet()

                override suspend fun record(
                    path: String,
                    delivered: Boolean,
                ) = Unit
            }
    }

    private object NeverUploader : PhotographUploader {
        override suspend fun upload(
            eventId: String,
            sourceKey: String,
            capturedAt: Long,
            fileName: String,
            contentType: String,
            bytes: ByteArray,
        ): UploadOutcome = UploadOutcome.Stored("photo")
    }

    private fun openedEvent() =
        FakeApi(
            events = listOf(summary("event-1", "Headshot day")),
            stations = mapOf("event-1" to listOf(station("station-1", "Bay 1", "Camera A"))),
        )

    /** Reads through the service the view model was given, rather than trusting the map. */
    private fun watching(
        viewModel: EventsViewModel,
        sourceKey: String,
    ): Boolean = viewModel.isWatchingForTest(sourceKey)

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
