package com.yellowtrack.platform.feature.display

import app.cash.turbine.test
import com.yellowtrack.platform.core.data.auth.AuthApi
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionStore
import com.yellowtrack.platform.core.data.auth.StoredSession
import com.yellowtrack.platform.core.data.event.EventsApi
import com.yellowtrack.platform.core.model.event.DeliveredResponse
import com.yellowtrack.platform.core.model.event.EventInviteResponse
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.QrMatrix
import com.yellowtrack.platform.core.model.event.RegistrationSummary
import com.yellowtrack.platform.core.model.event.SittingSummary
import com.yellowtrack.platform.core.model.event.StationSummary
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.display.presentation.DisplayContent
import com.yellowtrack.platform.feature.display.presentation.DisplayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

/**
 * A device on a table, and what it must not do while nobody is watching it.
 *
 * Most of these are about the two ways this can fail in a room rather than in a build. It can
 * show a code that no longer works, which sends people away believing they signed up; and it
 * can let somebody who is not the studio change what it shows, which quietly routes an hour of
 * sign-ups to the wrong event.
 */
class DisplayViewModelTest {
    @BeforeTest
    fun before() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun after() = Dispatchers.resetMain()

    // -- Choosing ---------------------------------------------------------------------------

    @Test
    fun `only events with a live code can be displayed`() =
        runTest {
            val api =
                FakeEventsApi(
                    events =
                        listOf(
                            summary("open-1", "Harbour Awards", signUpOpen = true),
                            summary("closed", "Last summer's wedding", signUpOpen = false),
                            summary("open-2", "Graduation day", signUpOpen = true),
                        ),
                )

            val viewModel = viewModel(api)
            advanceUntilIdle()

            assertEquals(
                listOf("Harbour Awards", "Graduation day"),
                viewModel.content().events.map { it.name },
            )
        }

    /**
     * Listing must not ask for an invite.
     *
     * Asking is what creates one. A device that requested a code per event to find out which
     * were open would open sign-ups on every event the studio had ever run, and nobody would
     * see it until strangers appeared on a wedding from two years ago.
     */
    @Test
    fun `showing the list does not ask for any codes`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))

            viewModel(api)
            advanceUntilIdle()

            assertEquals(0, api.invitesAsked, "listing asked for an invite, which issues one")
            assertEquals(0, api.codesAsked)
        }

    @Test
    fun `no open events is not an error`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("closed", "Last summer's wedding", signUpOpen = false)))

            val viewModel = viewModel(api)
            advanceUntilIdle()

            assertEquals(UiState.Empty, viewModel.uiState.value.content)
        }

    @Test
    fun `a server that cannot be reached is said so`() =
        runTest {
            val api = FakeEventsApi(events = emptyList(), listFails = RuntimeException("No connection."))

            val viewModel = viewModel(api)
            advanceUntilIdle()

            assertEquals(UiState.Error("No connection."), viewModel.uiState.value.content)
        }

    // -- Showing ----------------------------------------------------------------------------

    @Test
    fun `choosing an event puts its code on the screen`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api)
            advanceUntilIdle()

            viewModel.show("open-1")
            advanceUntilIdle()

            val showing = assertNotNull(viewModel.content().showing)
            assertEquals("Harbour Awards", showing.event.name)
            assertEquals("https://photos.example.test/join/the-token", showing.link)
            assertEquals(listOf("101", "010", "101"), showing.code.rows)
        }

    /**
     * The event chosen is the event shown.
     *
     * With one open event every path through this looks identical, which is how a screen ends
     * up displaying whichever event happened to be first. The consequence is the whole reason
     * the rest of this exists: a table with the wrong event's code on it collects an
     * afternoon of sign-ups against the wrong photographs, and looks entirely correct while
     * doing it.
     */
    @Test
    fun `the event chosen is the event shown`() =
        runTest {
            val api =
                FakeEventsApi(
                    events =
                        listOf(
                            summary("open-1", "Harbour Awards", signUpOpen = true),
                            summary("open-2", "Graduation day", signUpOpen = true),
                        ),
                )
            val viewModel = viewModel(api)
            advanceUntilIdle()

            viewModel.show("open-2")
            advanceUntilIdle()

            val showing = assertNotNull(viewModel.content().showing)
            assertEquals("Graduation day", showing.event.name)
            assertEquals(listOf("open-2"), api.invitedEvents, "the code fetched was for another event")
        }

    /**
     * The device on the table and the paper next to it are the same invitation.
     *
     * The invite call is idempotent, so an event that already has a code hands back that code
     * rather than a second one. A device that issued its own would silently orphan whatever
     * the studio had already printed, and half the codes in the room would stop working.
     */
    @Test
    fun `the code shown is the code already issued`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api)
            advanceUntilIdle()

            viewModel.show("open-1")
            advanceUntilIdle()

            assertEquals(1, api.invitesAsked)
            assertEquals(listOf("open-1"), api.invitedEvents)
        }

    @Test
    fun `a code that cannot be fetched leaves the list alone`() =
        runTest {
            val api =
                FakeEventsApi(
                    events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)),
                    codeFails = RuntimeException("No connection."),
                )
            val viewModel = viewModel(api)
            advanceUntilIdle()

            viewModel.show("open-1")
            advanceUntilIdle()

            assertNull(viewModel.content().showing, "a failed fetch showed an event anyway")
            assertEquals("No connection.", viewModel.uiState.value.problem)
            assertEquals(1, viewModel.content().events.size, "the list was taken away")
        }

    // -- Following the server ------------------------------------------------------------

    /**
     * A withdrawn code has to leave the table.
     *
     * This is the failure a printed card cannot avoid and a screen has no excuse for. Somebody
     * scans a code the studio has withdrawn, the server refuses them, and they walk away
     * believing they are signed up — and the studio finds out when the photographs have
     * nowhere to go.
     */
    @Test
    fun `a code withdrawn elsewhere stops being shown`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            // The studio withdraws it from the laptop across the room.
            api.events = listOf(summary("open-1", "Harbour Awards", signUpOpen = false))
            viewModel.poll()
            advanceUntilIdle()

            assertTrue(assertNotNull(viewModel.content().showing).withdrawn)
        }

    /** And the event stays named, rather than the screen going blank. */
    @Test
    fun `a withdrawn event is still named on the screen`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            api.events = emptyList()
            viewModel.poll()
            advanceUntilIdle()

            assertEquals("Harbour Awards", assertNotNull(viewModel.content().showing).event.name)
        }

    /**
     * A poll that failed says nothing about the invite.
     *
     * The venue's wifi dropping for one request must not take a working code off the table —
     * that would be a device that blanks itself for a few seconds every time the network
     * hiccups, in the middle of a queue of people trying to scan it.
     */
    @Test
    fun `a failed poll does not withdraw the code`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            api.listFails = RuntimeException("No connection.")
            viewModel.poll()
            advanceUntilIdle()

            assertFalse(assertNotNull(viewModel.content().showing).withdrawn, "a dropped request withdrew the code")
        }

    /** A code that comes back is shown again, rather than needing somebody to touch it. */
    @Test
    fun `a code reopened elsewhere is shown again`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            api.events = listOf(summary("open-1", "Harbour Awards", signUpOpen = false))
            viewModel.poll()
            advanceUntilIdle()

            api.events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true))
            viewModel.poll()
            advanceUntilIdle()

            assertFalse(assertNotNull(viewModel.content().showing).withdrawn)
        }

    /**
     * An event opened while the device is displaying is in the list somebody eventually sees.
     *
     * The studio opens sign-ups on the afternoon's event from the laptop and expects to walk
     * over, unlock, and find it — not to restart the application.
     */
    @Test
    fun `an event opened while displaying appears in the list`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            api.events =
                listOf(
                    summary("open-1", "Harbour Awards", signUpOpen = true),
                    summary("open-2", "Graduation day", signUpOpen = true),
                )
            viewModel.poll()
            advanceUntilIdle()

            assertEquals(
                listOf("Harbour Awards", "Graduation day"),
                viewModel.content().events.map { it.name },
            )
        }

    // -- The lock ---------------------------------------------------------------------------

    /**
     * The password is the only way back to the list.
     *
     * Everything else about this screen is cosmetic; this is the part that matters. A device
     * unattended among strangers, and one wrong tap routes the next hour of sign-ups to
     * another event with nobody the wiser.
     */
    @Test
    fun `the right password returns to the list`() =
        runTest {
            val auth = FakeAuthApi(password = "the studio's password")
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api, auth)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            viewModel.askToLeave()
            viewModel.typePassword("the studio's password")
            viewModel.confirmUnlock()
            advanceUntilIdle()

            assertNull(viewModel.content().showing)
        }

    @Test
    fun `the wrong password does not`() =
        runTest {
            val auth = FakeAuthApi(password = "the studio's password")
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api, auth)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            viewModel.askToLeave()
            viewModel.typePassword("a guess")
            viewModel.confirmUnlock()
            advanceUntilIdle()

            val showing = assertNotNull(viewModel.content().showing, "a wrong password left the code")
            assertEquals("That is not the password for this studio.", showing.unlock?.problem)
        }

    /** And it clears what was typed, so the next attempt starts from nothing. */
    @Test
    fun `a refused password is cleared`() =
        runTest {
            val auth = FakeAuthApi(password = "the studio's password")
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api, auth)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            viewModel.askToLeave()
            viewModel.typePassword("a guess")
            viewModel.confirmUnlock()
            advanceUntilIdle()

            assertEquals("", assertNotNull(viewModel.content().showing).unlock?.password)
        }

    /** Cancelling puts the code back without asking the server anything. */
    @Test
    fun `cancelling leaves the code where it was`() =
        runTest {
            val auth = FakeAuthApi(password = "the studio's password")
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api, auth)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            viewModel.askToLeave()
            viewModel.cancelLeaving()
            advanceUntilIdle()

            val showing = assertNotNull(viewModel.content().showing)
            assertNull(showing.unlock)
            assertEquals(0, auth.signInsAttempted, "cancelling asked the server about a password")
        }

    /**
     * A withdrawn code does not open the door either.
     *
     * The tempting shortcut — "the code is dead, so let anybody go back to the list" — hands
     * the device to whoever is standing next to it at the moment the studio closes sign-ups.
     */
    @Test
    fun `a withdrawn code still needs the password to leave`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            api.events = emptyList()
            viewModel.poll()
            advanceUntilIdle()

            assertNotNull(viewModel.content().showing, "a withdrawn code let the device off the event")
        }

    /**
     * Nor does every event closing while it is displaying.
     *
     * The list goes empty, and an empty list is the one state that replaces the screen. If
     * that reached the device it would leave the event with no password at all — and precisely
     * when the studio had closed the last code, which is the moment the screen most needs to
     * explain itself rather than reset.
     */
    @Test
    fun `an empty list does not take the device off its event`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))
            val viewModel = viewModel(api)
            advanceUntilIdle()
            viewModel.show("open-1")
            advanceUntilIdle()

            api.events = emptyList()
            viewModel.refresh()
            advanceUntilIdle()

            assertNotNull(viewModel.content().showing, "an empty refresh unlocked the device")
        }

    @Test
    fun `the studio's name is shown while choosing`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))

            val viewModel = viewModel(api)
            advanceUntilIdle()

            assertEquals("Harbourline Photography", viewModel.content().studioName)
        }

    @Test
    fun `the screen starts out loading`() =
        runTest {
            val api = FakeEventsApi(events = listOf(summary("open-1", "Harbour Awards", signUpOpen = true)))

            viewModel(api).uiState.test {
                assertEquals(UiState.Loading, awaitItem().content)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -- Plumbing -----------------------------------------------------------------------------

    private fun DisplayViewModel.content(): DisplayContent =
        (uiState.value.content as UiState.Success<DisplayContent>).data

    private suspend fun viewModel(
        api: FakeEventsApi,
        auth: FakeAuthApi = FakeAuthApi(password = "the studio's password"),
    ): DisplayViewModel {
        val repository = AuthRepository(store = FakeSessionStore(storedSession()), api = auth)
        // Puts the repository in SignedIn, which is the only state this screen ever runs in.
        repository.restore(now = 0L)

        return DisplayViewModel(api = api, auth = repository)
    }

    private fun storedSession() =
        StoredSession(
            token = "a-token",
            expiresAt = Long.MAX_VALUE,
            accountId = "an-account",
            email = "studio@harbourline.test",
            name = "Ada Okafor",
            studioId = "a-studio",
            studioName = "Harbourline Photography",
        )

    private fun summary(
        id: String,
        name: String,
        signUpOpen: Boolean,
    ) = EventSummary(id = id, name = name, signUpOpen = signUpOpen)

    private class FakeSessionStore(
        private var session: StoredSession?,
    ) : SessionStore {
        override suspend fun read(): StoredSession? = session

        override suspend fun write(session: StoredSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }

        override val isHardwareBacked: Boolean = true
    }

    private class FakeAuthApi(
        private val password: String,
    ) : AuthApi {
        var signInsAttempted = 0

        override suspend fun signIn(
            email: String,
            password: String,
        ): StoredSession {
            signInsAttempted++

            if (password != this.password) throw RuntimeException("Invalid credentials.")

            return StoredSession(
                token = "a-fresh-token",
                expiresAt = Long.MAX_VALUE,
                accountId = "an-account",
                email = email,
                name = "Ada Okafor",
                studioId = "a-studio",
                studioName = "Harbourline Photography",
            )
        }

        override suspend fun signUp(
            email: String,
            password: String,
            name: String,
            studioName: String,
        ): StoredSession = throw UnsupportedOperationException()

        override suspend fun requestPasswordReset(email: String) = throw UnsupportedOperationException()

        override suspend fun resetPassword(
            email: String,
            code: String,
            newPassword: String,
        ) = throw UnsupportedOperationException()

        override suspend fun signOut(token: String) = throw UnsupportedOperationException()

        override suspend fun restoreAccount(
            email: String,
            password: String,
        ): StoredSession = throw UnsupportedOperationException()

        override suspend fun exportStudio(token: String): String = throw UnsupportedOperationException()

        override suspend fun deleteAccount(
            token: String,
            password: String,
        ): Long = throw UnsupportedOperationException()
    }

    private class FakeEventsApi(
        var events: List<EventSummary>,
        var listFails: Throwable? = null,
        private val codeFails: Throwable? = null,
    ) : EventsApi {
        var invitesAsked = 0
        var codesAsked = 0
        val invitedEvents = mutableListOf<String>()

        override suspend fun events(): List<EventSummary> {
            listFails?.let { throw it }

            return events
        }

        override suspend fun invite(eventId: String): EventInviteResponse {
            invitesAsked++
            invitedEvents += eventId

            return EventInviteResponse(
                token = "the-token",
                url = "https://photos.example.test/join/the-token",
            )
        }

        override suspend fun inviteCode(eventId: String): QrMatrix {
            codeFails?.let { throw it }
            codesAsked++

            return QrMatrix(size = 3, rows = listOf("101", "010", "101"))
        }

        override suspend fun createEvent(
            name: String,
            startsAt: Long?,
        ): String = throw UnsupportedOperationException()

        override suspend fun stations(eventId: String): List<StationSummary> = throw UnsupportedOperationException()

        override suspend fun openStation(
            eventId: String,
            name: String,
            sourceKey: String,
        ): String = throw UnsupportedOperationException()

        override suspend fun closeStation(
            eventId: String,
            stationId: String,
        ) = throw UnsupportedOperationException()

        override suspend fun inviteCard(eventId: String): String = throw UnsupportedOperationException()

        override suspend fun revokeInvite(eventId: String) = throw UnsupportedOperationException()

        override suspend fun registrations(eventId: String): List<RegistrationSummary> =
            throw UnsupportedOperationException()

        override suspend fun advance(
            eventId: String,
            stationId: String,
            registrationId: String,
        ) = throw UnsupportedOperationException()

        override suspend fun sittings(eventId: String): List<SittingSummary> = throw UnsupportedOperationException()

        override suspend fun deliver(
            eventId: String,
            registrationId: String,
        ): DeliveredResponse = throw UnsupportedOperationException()
    }
}
