package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import com.yellowtrack.platform.core.model.event.CreateEventRequest
import com.yellowtrack.platform.core.model.event.CreatedResponse
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.OpenStationRequest
import com.yellowtrack.platform.core.model.event.StationSummary
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Running an event from the studio's side, over HTTP.
 *
 * The two properties worth the most here are the ones a photographer meets in a room rather
 * than a programmer at a desk: a source can carry only one station at a time, and closing a
 * station has to be safe to do twice.
 */
class EventRoutesTest {
    // -- Events ---------------------------------------------------------------------------

    @Test
    fun `an event can be created and listed back`() =
        withServer { client ->
            val session = client.signUp()

            val created = client.createEvent(session, "Harbour Awards 2026", startsAt = 1_700_000_000_000)

            val events = client.events(session)
            val event = events.single { it.id == created }

            assertEquals("Harbour Awards 2026", event.name)
            assertEquals(1_700_000_000_000, event.startsAt)
            assertEquals(0, event.openStations)
        }

    /** A walk-up event has no announced start, and that is not a missing value to fill in. */
    @Test
    fun `an event with no start time is allowed`() =
        withServer { client ->
            val session = client.signUp()
            val created = client.createEvent(session, "Saturday walk-ups")

            assertNull(client.events(session).single { it.id == created }.startsAt)
        }

    @Test
    fun `an event with no name is refused`() =
        withServer { client ->
            val session = client.signUp()

            val response =
                client.post("/events") {
                    bearerAuth(session.token)
                    contentType(ContentType.Application.Json)
                    setBody(apiJson.encodeToString(CreateEventRequest("   ")))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        }

    /**
     * One studio must not see another's events.
     *
     * The listing has no studio in its SQL at all — row level security supplies it — so this
     * is what proves the policy is actually on rather than the query merely looking careful.
     */
    @Test
    fun `one studio cannot see another studio's events`() =
        withServer { client ->
            val harbourline = client.signUp()
            val other = client.signUp()

            val theirs = client.createEvent(harbourline, "Harbour Awards 2026")

            assertTrue(
                client.events(other).none { it.id == theirs },
                "another studio's event was visible",
            )
        }

    @Test
    fun `listing events refuses a caller with no token`() =
        withServer { client ->
            assertEquals(HttpStatusCode.Unauthorized, client.get("/events").status)
        }

    // -- Stations -------------------------------------------------------------------------

    @Test
    fun `a station can be opened and appears against its event`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Headshot day")

            val station = client.openStation(session, event, "Bay 1", "Camera A")

            val stations = client.stations(session, event)
            assertEquals(1, stations.size)
            assertEquals(station, stations.single().id)
            assertEquals("Camera A", stations.single().sourceKey)
            assertNull(stations.single().closedAt, "a station just opened should be open")
        }

    /** The list a studio opens on has to show this without being opened. */
    @Test
    fun `an open station is counted on the event summary`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Headshot day")
            val station = client.openStation(session, event, "Bay 1", "Camera A")

            assertEquals(1, client.events(session).single { it.id == event }.openStations)

            // And it stops being counted once closed — otherwise this is a count of stations
            // that ever existed, which would tell a studio nothing about what is still live.
            client.closeStation(session, event, station)

            assertEquals(0, client.events(session).single { it.id == event }.openStations)
        }

    /**
     * The conflict that happens in a room.
     *
     * Two photographers setting up on one camera, or a station left open from the morning. A
     * photograph arriving on that source could belong to either station, so the second is
     * refused — and refused with the source named, because "conflict" alone does not tell
     * somebody holding a camera what to do next.
     */
    @Test
    fun `a source can only carry one open station`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Headshot day")
            client.openStation(session, event, "Bay 1", "Camera A")

            val response = client.openStationResponse(session, event, "Bay 2", "Camera A")

            assertEquals(HttpStatusCode.Conflict, response.status, response.bodyAsText())
            val body = response.bodyAsText()

            assertTrue("Camera A" in body, "the answer should name the source: $body")
        }

    /**
     * And the collision must not cross a tenant.
     *
     * "Camera A" is what everybody calls their first camera. If the index behind this were
     * global rather than per studio, one studio opening a station would refuse another
     * studio's — a refusal that also leaks that the other studio exists.
     */
    @Test
    fun `two studios may each have a station on the same source name`() =
        withServer { client ->
            val harbourline = client.signUp()
            val other = client.signUp()

            client.openStation(harbourline, client.createEvent(harbourline, "Theirs"), "Bay 1", "Camera A")

            val response =
                client.openStationResponse(other, client.createEvent(other, "Ours"), "Bay 1", "Camera A")

            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        }

    /** A station closed is a source freed, which is the point of closing one. */
    @Test
    fun `closing a station frees its source`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Headshot day")
            val station = client.openStation(session, event, "Bay 1", "Camera A")

            client.closeStation(session, event, station)

            val response = client.openStationResponse(session, event, "Bay 1 again", "Camera A")
            assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
        }

    /**
     * Closing twice is not an error.
     *
     * A photographer packing up taps this, and so might a colleague. The state being asked
     * for is "this station is closed", and it is closed either way.
     */
    @Test
    fun `closing a station twice is not an error`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Headshot day")
            val station = client.openStation(session, event, "Bay 1", "Camera A")

            assertEquals(HttpStatusCode.NoContent, client.closeStation(session, event, station).status)
            assertEquals(HttpStatusCode.NoContent, client.closeStation(session, event, station).status)
        }

    /**
     * A station identifier is guessable in the way any identifier is, and closing somebody
     * else's mid-event would send their photographs to the gallery instead of to the person
     * in front of the camera.
     */
    @Test
    fun `one studio cannot close another studio's station`() =
        withServer { client ->
            val harbourline = client.signUp()
            val other = client.signUp()

            val event = client.createEvent(harbourline, "Headshot day")
            val station = client.openStation(harbourline, event, "Bay 1", "Camera A")

            client.closeStation(other, event, station)

            assertNull(
                client.stations(harbourline, event).single().closedAt,
                "another studio closed a station it cannot see",
            )
        }

    @Test
    fun `a station with no source is refused`() =
        withServer { client ->
            val session = client.signUp()
            val event = client.createEvent(session, "Headshot day")

            val response = client.openStationResponse(session, event, "Bay 1", "  ")

            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        }

    // -- Plumbing ---------------------------------------------------------------------------

    private fun withServer(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(TestDatabase.database) }
            block(client)
        }

    private suspend fun HttpClient.signUp(): SessionResponse {
        val email = "events-${counter++}-${System.nanoTime()}@harbourline.test"
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
        startsAt: Long? = null,
    ): String {
        val response =
            post("/events") {
                bearerAuth(session.token)
                contentType(ContentType.Application.Json)
                setBody(apiJson.encodeToString(CreateEventRequest(name, startsAt)))
            }

        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        return apiJson.decodeFromString<CreatedResponse>(response.bodyAsText()).id
    }

    private suspend fun HttpClient.events(session: SessionResponse): List<EventSummary> {
        val response = get("/events") { bearerAuth(session.token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.stations(
        session: SessionResponse,
        eventId: String,
    ): List<StationSummary> {
        val response = get("/events/$eventId/stations") { bearerAuth(session.token) }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())

        return apiJson.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.openStationResponse(
        session: SessionResponse,
        eventId: String,
        name: String,
        sourceKey: String,
    ) = post("/events/$eventId/stations") {
        bearerAuth(session.token)
        contentType(ContentType.Application.Json)
        setBody(apiJson.encodeToString(OpenStationRequest(name, sourceKey)))
    }

    private suspend fun HttpClient.openStation(
        session: SessionResponse,
        eventId: String,
        name: String,
        sourceKey: String,
    ): String {
        val response = openStationResponse(session, eventId, name, sourceKey)
        assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())

        return apiJson.decodeFromString<CreatedResponse>(response.bodyAsText()).id
    }

    private suspend fun HttpClient.closeStation(
        session: SessionResponse,
        eventId: String,
        stationId: String,
    ) = post("/events/$eventId/stations/$stationId/close") { bearerAuth(session.token) }

    private companion object {
        private var counter = 0
    }
}
