package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import com.yellowtrack.platform.core.model.event.AdvanceStationRequest
import com.yellowtrack.platform.core.model.event.CreateEventRequest
import com.yellowtrack.platform.core.model.event.CreatedResponse
import com.yellowtrack.platform.core.model.event.EventInviteResponse
import com.yellowtrack.platform.core.model.event.InvitedEventResponse
import com.yellowtrack.platform.core.model.event.OpenStationRequest
import com.yellowtrack.platform.core.model.event.RegistrationSummary
import com.yellowtrack.platform.core.model.event.SignUpToEventRequest
import com.yellowtrack.platform.core.model.event.SittingSummary
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A whole event, using nothing but HTTP.
 *
 * Every other test in this file's neighbourhood calls `Events` directly, and that is exactly
 * how the gap this exists for survived: `advanceSlot` had no route at all, so no slot could
 * ever be created by a studio, every photograph routed to the event's gallery, and the entire
 * delivery path was unreachable. Nothing failed — routing to the gallery is the *correct*
 * behaviour when no slot is open, and there was simply never a slot.
 *
 * So this test is deliberately written the long way round: no domain classes, no shortcuts
 * into the database except to read back what should have happened. If a step in the story a
 * studio has to perform has no route, this stops compiling or stops passing.
 */
class EventEndToEndTest {
    @Test
    fun `a studio can run an event from sign-up to delivery over http alone`() =
        withServer { client ->
            // 1. A studio, and an event.
            val studio = client.signUpStudio()
            val event = client.createEvent(studio, "Harbour Awards 2026")

            // 2. The code that goes on the banner.
            val invite = client.invite(studio, event)
            assertTrue(invite.url.endsWith("/join/${invite.token}"), invite.url)

            // 3. Somebody scans it and signs up. No session anywhere in these two calls.
            val scanned = client.get("/api/join/${invite.token}")
            assertEquals(HttpStatusCode.OK, scanned.status, scanned.bodyAsText())
            assertEquals(
                "Harbour Awards 2026",
                apiJson.decodeFromString<InvitedEventResponse>(scanned.bodyAsText()).eventName,
            )
            assertEquals(HttpStatusCode.NoContent, client.join(invite.token, "guest@example.test", "Ada Guest").status)

            // 4. The studio sees them.
            val registration = client.registrations(studio, event).single()
            assertEquals("guest@example.test", registration.email)

            // 5. A station on a camera, and that person seated at it.
            val station = client.openStation(studio, event, "Bay 1", "Camera A")
            val slot = client.advance(studio, event, station, registration.id)

            // 6. Two photographs arrive from the watched folder.
            //
            // Storage is unconfigured in tests, so these are refused — which is the honest
            // stopping point for an in-process test and is covered by PhotographRouteTest.
            // The rows are written directly so the rest of the story can be told.
            repeat(2) { photographIn(slot, event, studio.studioId) }

            // 7. The sitting, as the studio's list shows it.
            val sitting = client.sittings(studio, event).single { it.id == slot }
            assertEquals("guest@example.test", sitting.email)
            assertEquals("Bay 1", sitting.stationName)
            assertEquals(2, sitting.photographs)
            assertNull(sitting.closedAt, "the sitting should still be open")
            assertNull(sitting.deliveredAt)

            // 8. Delivery is refused while it is open — the hold ADR 0013 describes.
            val early = client.deliver(studio, event, slot)
            assertEquals(HttpStatusCode.Conflict, early.status, early.bodyAsText())

            // 9. The photographer finishes.
            client.closeStation(studio, event, station)
            assertNotNull(client.sittings(studio, event).single { it.id == slot }.closedAt)

            // 10. And the studio hands it over. Mail is unconfigured here, so this is where an
            //     in-process run stops — 503, and deliberately *not* marked delivered, which
            //     is the property EventDeliveryTest pins.
            val delivery = client.deliver(studio, event, slot)
            assertEquals(HttpStatusCode.ServiceUnavailable, delivery.status, delivery.bodyAsText())
            assertNull(
                client.sittings(studio, event).single { it.id == slot }.deliveredAt,
                "a delivery that could not be sent was marked as done",
            )
        }

    /**
     * The half of the story that has no session in it at all.
     *
     * Worth its own test because the public pages are the only thing a guest ever touches, and
     * they must work with no credential of any kind.
     */
    @Test
    fun `a guest needs no account for any step they perform`() =
        withServer { client ->
            val studio = client.signUpStudio()
            val event = client.createEvent(studio, "Harbour Awards 2026")
            val invite = client.invite(studio, event)

            // The page, the event's name, the sign-up, and the gallery: no Authorization header
            // on any of them.
            assertEquals(HttpStatusCode.OK, client.get("/join/${invite.token}").status)
            assertEquals(HttpStatusCode.OK, client.get("/api/join/${invite.token}").status)
            assertEquals(HttpStatusCode.NoContent, client.join(invite.token, "guest@example.test").status)
            assertEquals(HttpStatusCode.OK, client.get("/gallery/anything").status)
        }

    // -- What advancing must refuse ------------------------------------------------------------

    /**
     * A finished station must not take another sitting.
     *
     * Unchecked until the route went in. The slot would be created, photographs whose capture
     * time fell inside its window would route into it, and somebody would be emailed a sitting
     * that happened after the camera had been packed away.
     */
    @Test
    fun `a closed station cannot be advanced`() =
        withServer { client ->
            val studio = client.signUpStudio()
            val event = client.createEvent(studio, "Harbour Awards 2026")
            val invite = client.invite(studio, event)
            client.join(invite.token, "guest@example.test")
            val registration = client.registrations(studio, event).single()

            val station = client.openStation(studio, event, "Bay 1", "Camera A")
            client.closeStation(studio, event, station)

            val response = client.advanceResponse(studio, event, station, registration.id)

            assertEquals(HttpStatusCode.Conflict, response.status, response.bodyAsText())
        }

    /**
     * And somebody from another event must not be seatable.
     *
     * Row level security already hides another *studio's* registrations. This is the narrower
     * question it cannot answer: one studio running a morning and an afternoon event must not
     * be able to seat a morning guest into the afternoon, which would send them a stranger's
     * sitting.
     */
    @Test
    fun `somebody registered for another event cannot be advanced`() =
        withServer { client ->
            val studio = client.signUpStudio()
            val morning = client.createEvent(studio, "Morning")
            val afternoon = client.createEvent(studio, "Afternoon")

            val morningInvite = client.invite(studio, morning)
            client.join(morningInvite.token, "guest@example.test")
            val theirRegistration = client.registrations(studio, morning).single().id

            val afternoonStation = client.openStation(studio, afternoon, "Bay 1", "Camera A")

            val response = client.advanceResponse(studio, afternoon, afternoonStation, theirRegistration)

            assertEquals(HttpStatusCode.NotFound, response.status, response.bodyAsText())
        }

    /** Advancing again closes the previous sitting rather than leaving two open. */
    @Test
    fun `advancing to the next person closes the one before`() =
        withServer { client ->
            val studio = client.signUpStudio()
            val event = client.createEvent(studio, "Harbour Awards 2026")
            val invite = client.invite(studio, event)
            client.join(invite.token, "first@example.test")
            client.join(invite.token, "second@example.test")

            val registrations = client.registrations(studio, event)
            val station = client.openStation(studio, event, "Bay 1", "Camera A")

            val first = client.advance(studio, event, station, registrations.first().id)
            client.advance(studio, event, station, registrations.last().id)

            val sittings = client.sittings(studio, event)
            assertEquals(2, sittings.size)
            assertNotNull(sittings.single { it.id == first }.closedAt, "the first sitting stayed open")
            assertEquals(1, sittings.count { it.closedAt == null }, "more than one sitting is open")
        }

    /** One studio must not read another's sign-ups. */
    @Test
    fun `one studio cannot see another studio's registrations`() =
        withServer { client ->
            val harbourline = client.signUpStudio()
            val other = client.signUpStudio()
            val event = client.createEvent(harbourline, "Harbour Awards 2026")
            client.join(client.invite(harbourline, event).token, "guest@example.test")

            assertTrue(client.registrations(other, event).isEmpty(), "another studio's sign-ups were visible")
        }

    @Test
    fun `advancing a station that does not exist is refused`() =
        withServer { client ->
            val studio = client.signUpStudio()
            val event = client.createEvent(studio, "Harbour Awards 2026")
            client.join(client.invite(studio, event).token, "guest@example.test")
            val registration = client.registrations(studio, event).single().id

            val response = client.advanceResponse(studio, event, "not-a-station", registration)
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.NotFound, response.status, body)
            // The message, not just the status. Without the station check the request still
            // 404s — by way of "nobody by that registration is signed up", because an unknown
            // station yields no event and the registration then matches nothing. Same code,
            // and it sends a photographer to look at the wrong thing.
            assertTrue("station" in body.lowercase(), "the refusal should name the station: $body")
        }

    // -- Each list belongs to its own event ------------------------------------------------------

    /**
     * Two events on one day is the ordinary case, not the exotic one.
     *
     * Row level security scopes these to the studio and stops there — it has no opinion about
     * which of a studio's own events a row belongs to. Both listings filter by event in SQL,
     * and nothing but a test with two events populated can tell whether they do: with only
     * one event in play, an unfiltered query returns exactly the right answer.
     */
    @Test
    fun `registrations are listed only for their own event`() =
        withServer { client ->
            val studio = client.signUpStudio()
            val morning = client.createEvent(studio, "Morning")
            val afternoon = client.createEvent(studio, "Afternoon")

            client.join(client.invite(studio, morning).token, "early@example.test")
            client.join(client.invite(studio, afternoon).token, "late@example.test")

            assertEquals(listOf("early@example.test"), client.registrations(studio, morning).map { it.email })
            assertEquals(listOf("late@example.test"), client.registrations(studio, afternoon).map { it.email })
        }

    @Test
    fun `sittings are listed only for their own event`() =
        withServer { client ->
            val studio = client.signUpStudio()
            val morning = client.createEvent(studio, "Morning")
            val afternoon = client.createEvent(studio, "Afternoon")

            client.join(client.invite(studio, morning).token, "early@example.test")
            client.join(client.invite(studio, afternoon).token, "late@example.test")

            val morningSlot =
                client.advance(
                    studio,
                    morning,
                    client.openStation(studio, morning, "Bay 1", "Camera A"),
                    client.registrations(studio, morning).single().id,
                )
            val afternoonSlot =
                client.advance(
                    studio,
                    afternoon,
                    client.openStation(studio, afternoon, "Bay 2", "Camera B"),
                    client.registrations(studio, afternoon).single().id,
                )

            assertEquals(listOf(morningSlot), client.sittings(studio, morning).map { it.id })
            assertEquals(listOf(afternoonSlot), client.sittings(studio, afternoon).map { it.id })
        }

    // -- Plumbing --------------------------------------------------------------------------------

    private fun withServer(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(TestDatabase.database) }
            block(client)
        }

    /** Written straight in, because storage is unconfigured in tests and the upload route 503s. */
    private fun photographIn(
        slotId: String,
        eventId: String,
        studioId: String,
    ) {
        val objectId = UUID.randomUUID().toString()

        TestDatabase.database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO stored_object(id, studio_id, object_key, content_type, size_bytes, created_at)
                    VALUES (?, ?, ?, 'image/jpeg', 1, 0)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, objectId)
                    statement.setString(2, studioId)
                    statement.setString(3, "$studioId/$objectId")
                    statement.executeUpdate()
                }

            connection
                .prepareStatement(
                    """
                    INSERT INTO event_photo(id, studio_id, event_id, stored_object_id, slot_id, captured_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, UUID.randomUUID().toString())
                    statement.setString(2, studioId)
                    statement.setString(3, eventId)
                    statement.setString(4, objectId)
                    statement.setString(5, slotId)
                    statement.setLong(6, System.currentTimeMillis())
                    statement.executeUpdate()
                }
        }
    }

    private suspend fun HttpClient.signUpStudio(): SessionResponse {
        val email = "chain-${counter++}-${System.nanoTime()}@harbourline.test"
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

    private suspend fun HttpClient.invite(
        session: SessionResponse,
        eventId: String,
    ): EventInviteResponse {
        val response = post("/events/$eventId/invite") { bearerAuth(session.token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.join(
        token: String,
        email: String,
        name: String? = null,
    ) = post("/api/join/$token") {
        contentType(ContentType.Application.Json)
        setBody(apiJson.encodeToString(SignUpToEventRequest(email, name)))
    }

    private suspend fun HttpClient.registrations(
        session: SessionResponse,
        eventId: String,
    ): List<RegistrationSummary> {
        val response = get("/events/$eventId/registrations") { bearerAuth(session.token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
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

    private suspend fun HttpClient.advanceResponse(
        session: SessionResponse,
        eventId: String,
        stationId: String,
        registrationId: String,
    ) = post("/events/$eventId/stations/$stationId/advance") {
        bearerAuth(session.token)
        contentType(ContentType.Application.Json)
        setBody(apiJson.encodeToString(AdvanceStationRequest(registrationId)))
    }

    private suspend fun HttpClient.advance(
        session: SessionResponse,
        eventId: String,
        stationId: String,
        registrationId: String,
    ): String {
        val response = advanceResponse(session, eventId, stationId, registrationId)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        return apiJson.decodeFromString<CreatedResponse>(response.bodyAsText()).id
    }

    private suspend fun HttpClient.sittings(
        session: SessionResponse,
        eventId: String,
    ): List<SittingSummary> {
        val response = get("/events/$eventId/sittings") { bearerAuth(session.token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.closeStation(
        session: SessionResponse,
        eventId: String,
        stationId: String,
    ) = post("/events/$eventId/stations/$stationId/close") { bearerAuth(session.token) }

    private suspend fun HttpClient.deliver(
        session: SessionResponse,
        eventId: String,
        slotId: String,
    ) = post("/events/$eventId/sittings/$slotId/deliver") { bearerAuth(session.token) }

    private companion object {
        private var counter = 0
    }
}
