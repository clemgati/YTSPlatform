package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import com.yellowtrack.platform.core.model.event.AdvanceStationRequest
import com.yellowtrack.platform.core.model.event.CreateEventRequest
import com.yellowtrack.platform.core.model.event.CreatedResponse
import com.yellowtrack.platform.core.model.event.EventInviteResponse
import com.yellowtrack.platform.core.model.event.OpenStationRequest
import com.yellowtrack.platform.core.model.event.PhotographAccepted
import com.yellowtrack.platform.core.model.event.RegistrationSummary
import com.yellowtrack.platform.core.model.event.SignUpToEventRequest
import com.yellowtrack.platform.server.event.Events
import com.yellowtrack.platform.server.event.Routed
import com.yellowtrack.platform.server.event.correctForClientClock
import com.yellowtrack.platform.server.storage.ObjectStore
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The thirty-nine milliseconds that cost a photograph.
 *
 * On the first live run of `walk-event.py`, a photograph uploaded moments after a sitting was
 * opened went to the event's gallery instead of to the person in it. The capture time came
 * from a laptop 39ms behind the server, so it predated the slot it belonged to, and no slot
 * matched. Nothing failed: routing to the gallery is the correct answer when no slot is open.
 *
 * Both directions are wrong and only one is merely inconvenient. Behind, and the opening
 * photographs of a sitting fall to the gallery. Ahead, and photographs taken while the
 * *previous* person was still in front of the camera match the current slot — one person's
 * photographs delivered to another, which is what ADR 0013 exists to prevent.
 */
class ClientClockTest {
    /** A laptop five seconds behind reports a capture time five seconds early. */
    @Test
    fun `a client that is behind has its capture time corrected forward`() {
        val skew = 5_000L
        val trueCapture = 1_786_341_443_396L
        val serverNow = trueCapture + 200

        val corrected =
            correctForClientClock(
                capturedAt = trueCapture - skew,
                clientNow = serverNow - skew,
                serverNow = serverNow,
            )

        assertEquals(trueCapture, corrected, "the skew was not cancelled")
    }

    /** And the dangerous direction is corrected the same way. */
    @Test
    fun `a client that is ahead has its capture time corrected back`() {
        val skew = 90_000L
        val trueCapture = 1_786_341_443_396L
        val serverNow = trueCapture + 200

        val corrected =
            correctForClientClock(
                capturedAt = trueCapture + skew,
                clientNow = serverNow + skew,
                serverNow = serverNow,
            )

        assertEquals(trueCapture, corrected, "a clock running fast was not brought back")
    }

    /** The exact case that failed, to the millisecond. */
    @Test
    fun `the thirty-nine millisecond case now lands inside the sitting`() {
        val openedAt = 1_786_341_443_396L
        val reportedCapture = 1_786_341_443_357L

        // Uncorrected, this is what happened: 39ms before the sitting opened.
        assertTrue(reportedCapture < openedAt)

        // The laptop's clock read `openedAt - 39` when the server's read `openedAt + 60`.
        val corrected =
            correctForClientClock(
                capturedAt = reportedCapture,
                clientNow = openedAt - 39,
                serverNow = openedAt + 60,
            )

        assertTrue(corrected >= openedAt, "still outside the sitting: $corrected vs $openedAt")
    }

    /** An older client sends nothing, and is treated exactly as it was. */
    @Test
    fun `a client that sends no clock is left alone`() {
        val capturedAt = 1_786_341_443_357L

        assertEquals(capturedAt, correctForClientClock(capturedAt, clientNow = null, serverNow = 9_999L))
    }

    /** A client whose clock happens to be right is unchanged, give or take the wire. */
    @Test
    fun `a correct clock is barely moved`() {
        val trueCapture = 1_786_341_443_396L
        val latency = 40L

        val corrected =
            correctForClientClock(
                capturedAt = trueCapture,
                clientNow = trueCapture + 100,
                serverNow = trueCapture + 100 + latency,
            )

        assertEquals(trueCapture + latency, corrected)
    }

    // -- What it is for --------------------------------------------------------------------

    /**
     * The hazard itself, against the real routing.
     *
     * Written against `Events` rather than the route because the upload route needs an object
     * store to reach routing at all, and this is about which slot a time falls in — not about
     * storage. It is the failure the correction exists to prevent, pinned so the routing rule
     * cannot quietly change underneath it.
     */
    @Test
    fun `a capture time before the sitting opened falls to the gallery`() {
        val events = Events(TestDatabase.database)
        val studio = studio()
        val event = events.createEvent(studio, "Harbour Awards 2026")
        val registration = events.register(studio, event, "guest@example.test").id
        val station = events.openStation(studio, event, "Bay 1", "Camera A")

        val before = System.currentTimeMillis()
        events.advanceSlot(studio, station, registration)

        val routed =
            events.recordPhotograph(
                studioId = studio,
                eventId = event,
                sourceKey = "Camera A",
                storedObjectId = storedObject(studio),
                // One millisecond before the sitting opened, which is all it takes.
                capturedAt = before - 1,
            )

        assertIs<Routed.ToGallery>(routed, "a photograph from before the sitting was attributed to it")
    }

    /** And one taken inside it belongs to the person. */
    @Test
    fun `a capture time inside the sitting belongs to the person`() {
        val events = Events(TestDatabase.database)
        val studio = studio()
        val event = events.createEvent(studio, "Harbour Awards 2026")
        val registration = events.register(studio, event, "guest@example.test").id
        val station = events.openStation(studio, event, "Bay 1", "Camera A")
        events.advanceSlot(studio, station, registration)

        val routed =
            events.recordPhotograph(
                studioId = studio,
                eventId = event,
                sourceKey = "Camera A",
                storedObjectId = storedObject(studio),
                capturedAt = System.currentTimeMillis() + 1_000,
            )

        val toSlot = assertIs<Routed.ToSlot>(routed)
        assertEquals(registration, toSlot.registrationId)
    }

    // -- Through the route, which is where it actually has to happen ------------------------

    /**
     * The whole thing, over HTTP, with a laptop that is wrong.
     *
     * The correction being right is not the same as the route applying it, and the two were
     * separately true and separately untested: every mutation of the arithmetic died, and
     * deleting the call from the route survived. This is the test that would have caught the
     * live failure, and it needs an object store to exist at all — the upload answers 503
     * before it reaches routing otherwise, which is why nothing covered this before.
     */
    @Test
    fun `a photograph from a laptop five seconds behind still reaches the person`() =
        withStorage { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val registration = client.register(session, event, "guest@example.test")
            val station = client.openStation(session, event, "Bay 1", "Camera A")
            client.advance(session, event, station, registration)

            // Five seconds behind: both the capture time and the clock reading are wrong by
            // the same amount, exactly as a real laptop's would be.
            val skew = 5_000L
            val capturedAt = System.currentTimeMillis() - skew

            val response =
                client.post(
                    "/events/$event/photographs?source=Camera%20A" +
                        "&capturedAt=$capturedAt&clientNow=$capturedAt",
                ) {
                    bearerAuth(session.token)
                    contentType(ContentType.Image.JPEG)
                    setBody(byteArrayOf(1, 2, 3))
                }

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
            assertEquals(
                registration,
                apiJson.decodeFromString<PhotographAccepted>(response.bodyAsText()).registrationId,
                "the photograph went to the gallery — the clock correction was not applied",
            )
        }

    /** Without the correction the same upload is lost to the gallery, which is the bug. */
    @Test
    fun `the same photograph without a client clock goes to the gallery`() =
        withStorage { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Harbour Awards 2026")
            val registration = client.register(session, event, "guest@example.test")
            val station = client.openStation(session, event, "Bay 1", "Camera A")
            client.advance(session, event, station, registration)

            val capturedAt = System.currentTimeMillis() - 5_000

            val response =
                client.post("/events/$event/photographs?source=Camera%20A&capturedAt=$capturedAt") {
                    bearerAuth(session.token)
                    contentType(ContentType.Image.JPEG)
                    setBody(byteArrayOf(1, 2, 3))
                }

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
            assertNull(
                apiJson.decodeFromString<PhotographAccepted>(response.bodyAsText()).registrationId,
                "an uncorrected capture time from before the sitting should not have matched it",
            )
        }

    private fun withStorage(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(TestDatabase.database, objects = AcceptingStore) }
            block(client)
        }

    /** Takes anything and signs nothing. Enough for the route to get past storage. */
    private object AcceptingStore : ObjectStore {
        override fun put(
            key: String,
            contentType: String,
            bytes: ByteArray,
        ) = Unit

        override fun temporaryUrl(
            key: String,
            validFor: kotlin.time.Duration,
        ): String = "https://example.invalid/$key"

        override fun delete(keys: List<String>): Set<String> = keys.toSet()
    }

    private suspend fun HttpClient.signUp(): SessionResponse {
        val email = "clock-${counter++}-${System.nanoTime()}@harbourline.test"
        val response =
            post("/auth/sign-up") {
                contentType(ContentType.Application.Json)
                setBody(
                    apiJson.encodeToString(
                        SignUpRequest(email, "a long enough password", "Ada Okafor", "Harbourline Photography"),
                    ),
                )
            }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.createEvent(
        session: SessionResponse,
        name: String,
    ): String {
        val response =
            post("/events") {
                bearerAuth(session.token)
                contentType(ContentType.Application.Json)
                setBody(apiJson.encodeToString(CreateEventRequest(name)))
            }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        return apiJson.decodeFromString<CreatedResponse>(response.bodyAsText()).id
    }

    private suspend fun HttpClient.register(
        session: SessionResponse,
        eventId: String,
        email: String,
    ): String {
        val invite =
            apiJson.decodeFromString<EventInviteResponse>(
                post("/events/$eventId/invite") { bearerAuth(session.token) }.bodyAsText(),
            )
        post("/api/join/${invite.token}") {
            contentType(ContentType.Application.Json)
            setBody(apiJson.encodeToString(SignUpToEventRequest(email, "Ada", "Okafor")))
        }

        val registrations =
            apiJson.decodeFromString<List<RegistrationSummary>>(
                get("/events/$eventId/registrations") { bearerAuth(session.token) }.bodyAsText(),
            )

        return registrations.single().id
    }

    private suspend fun HttpClient.openStation(
        session: SessionResponse,
        eventId: String,
        name: String,
        sourceKey: String,
    ): String {
        val response =
            post("/events/$eventId/stations") {
                bearerAuth(session.token)
                contentType(ContentType.Application.Json)
                setBody(apiJson.encodeToString(OpenStationRequest(name, sourceKey)))
            }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        return apiJson.decodeFromString<CreatedResponse>(response.bodyAsText()).id
    }

    private suspend fun HttpClient.advance(
        session: SessionResponse,
        eventId: String,
        stationId: String,
        registrationId: String,
    ) {
        val response =
            post("/events/$eventId/stations/$stationId/advance") {
                bearerAuth(session.token)
                contentType(ContentType.Application.Json)
                setBody(apiJson.encodeToString(AdvanceStationRequest(registrationId)))
            }
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
    }

    private companion object {
        private var counter = 0
    }

    // -- Fixtures -------------------------------------------------------------------------

    private fun studio(): String {
        val id = "studio-${UUID.randomUUID()}"

        TestDatabase.connection().use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO studio(id, name, created_at, updated_at, version) VALUES (?, ?, 0, 0, 1)",
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, "Harbourline Photography")
                    statement.executeUpdate()
                }
        }

        return id
    }

    private fun storedObject(studioId: String): String {
        val id = UUID.randomUUID().toString()

        TestDatabase.database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO stored_object(id, studio_id, object_key, content_type, size_bytes, created_at)
                    VALUES (?, ?, ?, 'image/jpeg', 1, 0)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, studioId)
                    statement.setString(3, "$studioId/$id")
                    statement.executeUpdate()
                }
        }

        return id
    }
}
