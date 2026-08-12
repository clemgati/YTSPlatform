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
import com.yellowtrack.platform.core.export.Document
import com.yellowtrack.platform.core.export.DocumentSink
import com.yellowtrack.platform.core.export.SavedDocument
import com.yellowtrack.platform.core.model.event.DeliveredResponse
import com.yellowtrack.platform.core.model.event.EventInviteResponse
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.QrMatrix
import com.yellowtrack.platform.core.model.event.RegistrationSummary
import com.yellowtrack.platform.core.model.event.SignUpToEventRequest
import com.yellowtrack.platform.core.model.event.SittingSummary
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

    /**
     * A studio with no events can still create one.
     *
     * The previous version of this test asserted `UiState.Empty` and called it correct. That
     * state renders a message and nothing else, and the only way to create an event lives on
     * the success screen — so every new studio was shown "No events yet" and no way forward.
     * The test enshrined the dead end, and the render tests only ever covered populated
     * screens, so looking would not have caught it either.
     */
    @Test
    fun `a studio with no events can still create one`() =
        runTest(dispatcher) {
            val api = FakeApi()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()

            // Success with an empty list, not Empty: the screen is normal and has nothing on
            // it, which is what keeps the form reachable.
            val content = viewModel.uiState.value.content
            assertTrue(content is UiState.Success, "an empty list became a screen with no way out: $content")
            assertTrue(viewModel.content().events.isEmpty())

            viewModel.createEvent("Harbour Awards 2026")
            testScheduler.advanceUntilIdle()

            assertEquals(1, api.eventsCreated, "the first event could not be created")
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

    // -- The sign-up code, which is how anybody gets in at all --------------------------------

    /**
     * Saving the code is one action, not two.
     *
     * Until this existed the only way to obtain a sign-up link was to call the API by hand —
     * which is exactly what the walkthrough script does, and why an end-to-end test passed
     * while the studio had no way to start an event at all.
     */
    @Test
    fun `saving the sign-up code issues one and writes a printable file`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val sink = RecordingSink()
            val viewModel = viewModel(api, sink = sink)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            viewModel.printSignUpCode("event-1")
            testScheduler.advanceUntilIdle()

            assertEquals(1, api.inviteIssued)
            assertEquals(1, api.cardRequested)

            val document = sink.saved.single()
            assertEquals("sign-up-code.html", document.fileName, "it must be printable from a browser")
            assertTrue("printable card" in document.content)
            assertTrue(
                "Downloads" in
                    viewModel.uiState.value.note
                        .orEmpty(),
                viewModel.uiState.value.note
                    .orEmpty(),
            )
        }

    /** The link is shown as well as printed — a code photographs badly in some lighting. */
    @Test
    fun `saving the code shows the link on screen too`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            assertNull(viewModel.content().open!!.inviteUrl, "no code should exist before it is asked for")

            viewModel.printSignUpCode("event-1")
            testScheduler.advanceUntilIdle()

            assertTrue(
                viewModel
                    .content()
                    .open!!
                    .inviteUrl
                    .orEmpty()
                    .contains("/join/"),
                "the link is not on screen: ${viewModel.content().open!!.inviteUrl}",
            )
        }

    /**
     * Withdrawing is the only way to close a sign-up once something is printed.
     *
     * The link leaves the screen with it, because a link still shown is a link somebody reads
     * out to a guest who then cannot sign up.
     */
    @Test
    fun `withdrawing the code removes it from the screen`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.printSignUpCode("event-1")
            testScheduler.advanceUntilIdle()

            viewModel.withdrawSignUpCode("event-1")
            testScheduler.advanceUntilIdle()

            assertEquals(1, api.revoked)
            assertNull(viewModel.content().open!!.inviteUrl, "a withdrawn code is still shown")
        }

    /** A failure while saving must not claim a file was written. */
    @Test
    fun `a code that could not be saved says so`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            api.inviteFails = EventActionFailed("There is no such event.")
            val sink = RecordingSink()
            val viewModel = viewModel(api, sink = sink)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            viewModel.printSignUpCode("event-1")
            testScheduler.advanceUntilIdle()

            assertTrue(sink.saved.isEmpty(), "a file was written for a code that was never issued")
            assertNull(viewModel.uiState.value.note)
            assertTrue(
                "no such event" in
                    viewModel.uiState.value.problem
                        .orEmpty()
                        .lowercase(),
            )
        }

    // -- Keeping up with an event that is happening ------------------------------------------

    /**
     * Somebody who scans the code while the photographer is looking at the screen.
     *
     * This is the failure as it was reported: two people signed up, and the list to seat them
     * from still showed only the two who had signed up earlier. Everything was loaded once
     * when the event was opened and then frozen — on a screen whose entire premise is that
     * people sign up while the event is happening.
     */
    @Test
    fun `somebody who signs up while the event is open appears`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            assertEquals(
                2,
                viewModel
                    .content()
                    .open!!
                    .registrations.size,
            )

            // A guest scans the code. Nothing on the studio's side is touched.
            api.registrationsByEvent.getValue("event-1") +=
                RegistrationSummary(
                    id = "reg-3",
                    email = "barbara@example.test",
                    name = "Barbara",
                    registeredAt = 2_000,
                )

            viewModel.refreshOpenEvent()
            testScheduler.advanceUntilIdle()

            assertEquals(
                listOf("first@example.test", "second@example.test", "barbara@example.test"),
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .map { it.email },
                "a sign-up during the event never reached the screen",
            )
        }

    /** And the photograph count climbs while ingest is running, rather than freezing. */
    @Test
    fun `photograph counts keep up while the event is open`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.seat(
                "event-1",
                "station-1",
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
                    .id,
            )
            testScheduler.advanceUntilIdle()
            assertEquals(
                0,
                viewModel
                    .content()
                    .open!!
                    .sittings
                    .single()
                    .photographs,
            )

            api.photographIn("event-1", 3)
            viewModel.refreshOpenEvent()
            testScheduler.advanceUntilIdle()

            assertEquals(
                3,
                viewModel
                    .content()
                    .open!!
                    .sittings
                    .single()
                    .photographs,
                "the count froze while photographs were arriving",
            )
        }

    /** With no event open there is nothing to refresh, and asking must not go to the server. */
    @Test
    fun `refreshing with no event open asks the server nothing`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.closeEvent()
            testScheduler.advanceUntilIdle()

            val before = api.registrationsRead
            viewModel.refreshOpenEvent()
            testScheduler.advanceUntilIdle()

            assertEquals(before, api.registrationsRead, "it read an event nobody is looking at")
        }

    /**
     * A refresh that fails says nothing.
     *
     * It repeats every couple of seconds, and a photographer with a queue can do nothing about
     * a momentary failure. A real one surfaces the instant they touch anything.
     */
    @Test
    fun `a failed refresh does not put an error on the screen`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            api.registrationsFails = EventActionFailed("Could not reach the server.")
            viewModel.refreshOpenEvent()
            testScheduler.advanceUntilIdle()

            assertNull(viewModel.uiState.value.problem, "a background refresh interrupted the screen")
            assertTrue(viewModel.uiState.value.content is UiState.Success, "the screen was thrown away")
        }

    // -- Seating somebody, and sending them their photographs ---------------------------------

    /**
     * Opening an event loads who is signed up and what has been shot.
     *
     * All three lists together, because they are read as one thing — who is at which camera,
     * and what is owed a delivery.
     */
    @Test
    fun `opening an event loads its people and sittings`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            val open = assertNotNull(viewModel.content().open)
            assertEquals(listOf("first@example.test", "second@example.test"), open.registrations.map { it.email })
            assertTrue(open.sittings.isEmpty())
        }

    /**
     * Seating somebody is what makes photographs theirs.
     *
     * From that moment every frame off the camera belongs to this person, so the screen must
     * show what the server believes rather than what it hoped — hence the reload.
     */
    @Test
    fun `seating somebody puts them under the camera`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            val person =
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
            viewModel.seat("event-1", "station-1", person.id)
            testScheduler.advanceUntilIdle()

            val open = viewModel.content().open!!
            assertEquals(1, api.advanced)
            assertEquals(person.email, open.seated["Bay 1"]?.email, "nobody is shown under the camera")
        }

    /** Advancing closes the sitting before it, so only one person is ever seated. */
    @Test
    fun `seating the next person closes the one before`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            val people = viewModel.content().open!!.registrations
            viewModel.seat("event-1", "station-1", people[0].id)
            testScheduler.advanceUntilIdle()
            viewModel.seat("event-1", "station-1", people[1].id)
            testScheduler.advanceUntilIdle()

            val open = viewModel.content().open!!
            assertEquals(2, open.sittings.size)
            assertEquals(1, open.sittings.count { it.isOpen }, "more than one person is seated")
            assertEquals(people[1].email, open.seated["Bay 1"]?.email)
        }

    /**
     * A refusal from advancing leaves the screen usable.
     *
     * The server refuses a closed station and somebody from another event, and both arrive
     * while a photographer has a queue in front of them.
     */
    @Test
    fun `a refused seating says so and leaves the screen`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            api.advanceFails = EventActionFailed("That station is closed. Open it again first.")
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()

            viewModel.seat(
                "event-1",
                "station-1",
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
                    .id,
            )
            testScheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.content is UiState.Success, "the screen was replaced")
            assertTrue(
                "closed" in
                    viewModel.uiState.value.problem
                        .orEmpty(),
            )
        }

    /**
     * Nobody is under the camera once the station closes.
     *
     * Written against a station whose only sittings are finished, because the earlier test
     * could not tell "the open one" from "the last one" — with one closed and one open
     * sitting, both readings give the same answer, and a mutation dropping the open check
     * survived.
     */
    @Test
    fun `closing a station leaves nobody seated`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.seat(
                "event-1",
                "station-1",
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
                    .id,
            )
            testScheduler.advanceUntilIdle()

            viewModel.closeStation("event-1", "station-1")
            testScheduler.advanceUntilIdle()

            val open = viewModel.content().open!!
            assertEquals(1, open.sittings.size, "the sitting should still be listed")
            assertNull(open.seated["Bay 1"], "somebody is still shown under a closed camera")
        }

    // -- Delivery ----------------------------------------------------------------------------

    /**
     * A sitting cannot be sent while it is open, and the screen says why rather than only
     * disabling something.
     */
    @Test
    fun `an open sitting is not offered for delivery`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.seat(
                "event-1",
                "station-1",
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
                    .id,
            )
            testScheduler.advanceUntilIdle()

            // With photographs in it, so being open is the only thing refusing it. Without
            // them the emptiness check answers first and this proves nothing — which is how
            // a mutation dropping the open check survived.
            api.photographIn("event-1", 4)
            viewModel.refresh()
            testScheduler.advanceUntilIdle()

            val sitting =
                viewModel
                    .content()
                    .open!!
                    .sittings
                    .single()

            assertEquals(4, sitting.photographs)
            assertFalse(sitting.canDeliver, "an open sitting was offered for delivery")
            assertEquals("Still open", sitting.blockedBecause)
            assertTrue(
                viewModel
                    .content()
                    .open!!
                    .awaitingDelivery
                    .isEmpty(),
            )
        }

    /** A closed sitting with nothing in it is not work either, and says so differently. */
    @Test
    fun `a closed sitting with no photographs says what is missing`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.seat(
                "event-1",
                "station-1",
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
                    .id,
            )
            testScheduler.advanceUntilIdle()
            viewModel.closeStation("event-1", "station-1")
            testScheduler.advanceUntilIdle()

            val sitting =
                viewModel
                    .content()
                    .open!!
                    .sittings
                    .single()

            assertFalse(sitting.canDeliver)
            assertEquals("No photographs", sitting.blockedBecause)
        }

    /**
     * The job after an event: closed, holding photographs, not yet sent.
     *
     * A sitting nobody sends is a person who was photographed and got nothing, and this list
     * is the only place that is visible.
     */
    @Test
    fun `a closed sitting with photographs is waiting to be sent`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.seat(
                "event-1",
                "station-1",
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
                    .id,
            )
            testScheduler.advanceUntilIdle()
            api.photographIn("event-1", 3)
            viewModel.closeStation("event-1", "station-1")
            testScheduler.advanceUntilIdle()

            val waiting = viewModel.content().open!!.awaitingDelivery

            assertEquals(1, waiting.size)
            assertEquals(3, waiting.single().photographs)
            assertNull(waiting.single().blockedBecause)
        }

    @Test
    fun `sending a sitting marks it sent and says so`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.seat(
                "event-1",
                "station-1",
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
                    .id,
            )
            testScheduler.advanceUntilIdle()
            api.photographIn("event-1", 2)
            viewModel.closeStation("event-1", "station-1")
            testScheduler.advanceUntilIdle()

            viewModel.deliver(
                "event-1",
                viewModel
                    .content()
                    .open!!
                    .awaitingDelivery
                    .single()
                    .id,
            )
            testScheduler.advanceUntilIdle()

            assertEquals(1, api.delivered)
            assertTrue(
                viewModel
                    .content()
                    .open!!
                    .sittings
                    .single()
                    .isDelivered,
            )
            assertTrue(
                viewModel
                    .content()
                    .open!!
                    .awaitingDelivery
                    .isEmpty(),
                "it is still shown as work",
            )
            assertTrue(
                "first@example.test" in
                    viewModel.uiState.value.note
                        .orEmpty(),
            )
        }

    /** Sending twice mails once, and the second time says so rather than claiming a send. */
    @Test
    fun `sending an already sent sitting does not claim to have sent it`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.seat(
                "event-1",
                "station-1",
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
                    .id,
            )
            testScheduler.advanceUntilIdle()
            api.photographIn("event-1", 1)
            viewModel.closeStation("event-1", "station-1")
            testScheduler.advanceUntilIdle()
            val slot =
                viewModel
                    .content()
                    .open!!
                    .awaitingDelivery
                    .single()
                    .id

            viewModel.deliver("event-1", slot)
            testScheduler.advanceUntilIdle()
            viewModel.deliver("event-1", slot)
            testScheduler.advanceUntilIdle()

            assertEquals(1, api.delivered, "it was sent twice")
            assertTrue(
                "already had" in
                    viewModel.uiState.value.note
                        .orEmpty(),
                viewModel.uiState.value.note
                    .orEmpty(),
            )
        }

    /** A server that cannot send mail must not leave the studio thinking it did. */
    @Test
    fun `a failed delivery says so and does not mark it sent`() =
        runTest(dispatcher) {
            val api = eventWithPeople()
            val viewModel = viewModel(api)
            testScheduler.advanceUntilIdle()
            viewModel.open("event-1")
            testScheduler.advanceUntilIdle()
            viewModel.seat(
                "event-1",
                "station-1",
                viewModel
                    .content()
                    .open!!
                    .registrations
                    .first()
                    .id,
            )
            testScheduler.advanceUntilIdle()
            api.photographIn("event-1", 1)
            viewModel.closeStation("event-1", "station-1")
            testScheduler.advanceUntilIdle()

            api.deliverFails = EventActionFailed("This server cannot send mail. Nothing was sent.")
            viewModel.deliver(
                "event-1",
                viewModel
                    .content()
                    .open!!
                    .awaitingDelivery
                    .single()
                    .id,
            )
            testScheduler.advanceUntilIdle()

            assertTrue(
                "cannot send mail" in
                    viewModel.uiState.value.problem
                        .orEmpty(),
            )
            assertNull(viewModel.uiState.value.note)
            assertFalse(
                viewModel
                    .content()
                    .open!!
                    .sittings
                    .single()
                    .isDelivered,
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
        sink: DocumentSink = RecordingSink(),
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
        sink = sink,
    ).also { viewModel ->
        // `uiState` is shared `WhileSubscribed`, so with nobody collecting it never leaves its
        // initial value and every assertion below would read `Loading`. Compose subscribes for
        // as long as the screen is on show; this is that, for as long as the test runs.
        backgroundScope.launch { viewModel.uiState.collect {} }
    }

    /** Records what would have been written, and where it says it went. */
    private class RecordingSink : DocumentSink {
        val saved = mutableListOf<Document>()

        override suspend fun save(document: Document): SavedDocument {
            saved += document

            return SavedDocument(fileName = document.fileName, location = "~/Downloads/${document.fileName}")
        }
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

    private fun eventWithPeople(): FakeApi {
        val api =
            FakeApi(
                events = listOf(summary("event-1", "Headshot day")),
                stations = mapOf("event-1" to listOf(station("station-1", "Bay 1", "Camera A"))),
            )
        api.registrationsByEvent["event-1"] =
            mutableListOf(
                RegistrationSummary(
                    id = "reg-1",
                    email = "first@example.test",
                    name = "Ada Okafor",
                    registeredAt = 1_000,
                ),
                RegistrationSummary(id = "reg-2", email = "second@example.test", name = null, registeredAt = 900),
            )

        return api
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
            // An index loop rather than the Java 8 list API, which needs an opt-in on
            // Kotlin/Native — where these tests also run.
            sittingsByEvent[eventId]?.let { list ->
                list.indices.forEach { i -> if (list[i].closedAt == null) list[i] = list[i].copy(closedAt = 3_000) }
            }
        }

        // -- Sittings and the people in them --------------------------------------------

        val registrationsByEvent = mutableMapOf<String, MutableList<RegistrationSummary>>()
        val sittingsByEvent = mutableMapOf<String, MutableList<SittingSummary>>()
        var advanceFails: Throwable? = null
        var deliverFails: Throwable? = null
        var delivered = 0
        var advanced = 0

        // -- The sign-up code ------------------------------------------------------------

        var inviteIssued = 0
        var revoked = 0
        var cardRequested = 0
        private var token = "the-token"

        var inviteFails: Throwable? = null

        override suspend fun invite(eventId: String): EventInviteResponse {
            inviteFails?.let { throw it }
            inviteIssued++

            return EventInviteResponse(token = token, url = "https://photos.example.test/join/$token")
        }

        var codeRequested = 0

        override suspend fun inviteCode(eventId: String): QrMatrix {
            codeRequested++

            // A shape rather than a code: nothing here draws it, and the drawing is tested
            // where it happens.
            return QrMatrix(size = 3, rows = listOf("101", "010", "101"))
        }

        var joined = mutableListOf<SignUpToEventRequest>()

        override suspend fun joinEvent(
            token: String,
            request: SignUpToEventRequest,
        ) {
            joined += request
        }

        override suspend fun inviteCard(eventId: String): String {
            cardRequested++

            return "<html><body>a printable card for $eventId</body></html>"
        }

        override suspend fun revokeInvite(eventId: String) {
            revoked++
            // Reissuing gives a different code, which is what kills the printed one.
            token = "a-different-token"
        }

        var registrationsRead = 0
        var registrationsFails: Throwable? = null

        override suspend fun registrations(eventId: String): List<RegistrationSummary> {
            registrationsRead++
            registrationsFails?.let { throw it }

            return registrationsByEvent[eventId].orEmpty()
        }

        override suspend fun advance(
            eventId: String,
            stationId: String,
            registrationId: String,
        ): String {
            advanceFails?.let { throw it }
            advanced++
            val id = "sitting-$advanced"
            val person = registrationsByEvent[eventId].orEmpty().first { it.id == registrationId }
            val station = stationsByEvent[eventId].orEmpty().first { it.id == stationId }

            // Advancing closes whatever was open, as the server does.
            val existing = sittingsByEvent.getOrPut(eventId) { mutableListOf() }
            existing.indices.forEach { i ->
                if (existing[i].closedAt == null) existing[i] = existing[i].copy(closedAt = 2_500)
            }

            sittingsByEvent.getValue(eventId) +=
                SittingSummary(
                    id = id,
                    registrationId = registrationId,
                    email = person.email,
                    name = person.name,
                    stationName = station.name,
                    openedAt = 2_000,
                    closedAt = null,
                    deliveredAt = null,
                    photographs = 0,
                )

            return id
        }

        override suspend fun sittings(eventId: String): List<SittingSummary> = sittingsByEvent[eventId].orEmpty()

        /** Photographs land in whichever sitting is open, as the server routes them. */
        fun photographIn(
            eventId: String,
            count: Int,
        ) {
            val list = sittingsByEvent.getValue(eventId)
            val index = list.indexOfFirst { it.closedAt == null }
            if (index >= 0) list[index] = list[index].copy(photographs = list[index].photographs + count)
        }

        override suspend fun deliver(
            eventId: String,
            slotId: String,
        ): DeliveredResponse {
            deliverFails?.let { throw it }

            val list = sittingsByEvent.getValue(eventId)
            val index = list.indexOfFirst { it.id == slotId }
            val already = list[index].deliveredAt != null
            if (!already) {
                delivered++
                list[index] = list[index].copy(deliveredAt = 4_000)
            }

            return DeliveredResponse(
                email = list[index].email,
                photographs = list[index].photographs,
                sentNow = !already,
            )
        }
    }
}
