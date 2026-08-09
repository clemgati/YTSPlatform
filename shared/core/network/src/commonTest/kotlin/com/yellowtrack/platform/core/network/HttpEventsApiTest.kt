package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.event.EventActionFailed
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What the studio's application is told when running an event goes wrong.
 *
 * The message is the feature. A photographer standing at a camera can act on "a station is
 * already open on Camera A" and can do nothing at all with "that did not work", so the
 * server's own words have to survive the trip.
 */
class HttpEventsApiTest {
    @Test
    fun `events are listed`() =
        runTest {
            val events =
                api {
                    respondJson("""[{"id":"event-1","name":"Headshot day","openStations":2,"photographs":40}]""")
                }.events()

            assertEquals(1, events.size)
            assertEquals("Headshot day", events.single().name)
            assertEquals(2, events.single().openStations)
        }

    /**
     * The refusal a photographer meets, carried through word for word.
     *
     * This is the one message in the feature that tells somebody what to do next, and the
     * only place it could be lost is here.
     */
    @Test
    fun `a source already in use comes back with the camera named`() =
        runTest {
            val failure =
                assertFailsWith<EventActionFailed> {
                    api {
                        respond(
                            """{"error":"A station is already open on Camera A. Close it before opening another."}""",
                            HttpStatusCode.Conflict,
                            headersOf("Content-Type", ContentType.Application.Json.toString()),
                        )
                    }.openStation("event-1", "Bay 2", "Camera A")
                }

            assertTrue("Camera A" in failure.message.orEmpty(), "the camera was not named: ${failure.message}")
        }

    /** A refusal with no readable body still has to say something rather than crash. */
    @Test
    fun `an unreadable refusal still reports its status`() =
        runTest {
            val failure =
                assertFailsWith<EventActionFailed> {
                    api { respondError(HttpStatusCode.BadGateway) }.events()
                }

            assertTrue("502" in failure.message.orEmpty(), failure.message.orEmpty())
        }

    @Test
    fun `an unreachable server is reported as such`() =
        runTest {
            val failure =
                assertFailsWith<EventActionFailed> {
                    api { throw RuntimeException("connection reset") }.events()
                }

            assertTrue("reach" in failure.message.orEmpty(), failure.message.orEmpty())
        }

    /**
     * Not signed in is not a network failure, and must not be dressed as one.
     *
     * "Could not reach the server" sends somebody to check the venue's wifi over a problem
     * that a sign-in fixes.
     */
    @Test
    fun `no token is reported as not being signed in`() =
        runTest {
            val failure =
                assertFailsWith<EventActionFailed> {
                    api(token = null) { respondJson("[]") }.events()
                }

            assertTrue("signed in" in failure.message.orEmpty(), failure.message.orEmpty())
        }

    /**
     * A 200 whose body will not decode means this build and the server disagree about the
     * contract. The fix is a deployment, so it is said as itself.
     */
    @Test
    fun `a body this version cannot read is reported as a version disagreement`() =
        runTest {
            val failure =
                assertFailsWith<EventActionFailed> {
                    api { respondJson("""{"unexpected":true}""") }.events()
                }

            assertTrue("understand" in failure.message.orEmpty(), failure.message.orEmpty())
        }

    /** Closing is a 204 with no body, which must not be read as a failure to decode one. */
    @Test
    fun `closing a station succeeds on an empty answer`() =
        runTest {
            api { respond("", HttpStatusCode.NoContent) }.closeStation("event-1", "station-1")
        }

    @Test
    fun `creating an event returns its identifier`() =
        runTest {
            val id =
                api {
                    respondJson("""{"id":"event-9"}""", HttpStatusCode.Created)
                }.createEvent("Headshot day", null)

            assertEquals("event-9", id)
        }

    // -- Fixtures ---------------------------------------------------------------------------

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(body, status, headersOf("Content-Type", ContentType.Application.Json.toString()))

    private fun api(
        token: String? = "a-token",
        handler: io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData,
    ): HttpEventsApi {
        val client =
            HttpClient(MockEngine { handler() }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

        return HttpEventsApi(client, "https://api.example.invalid", { token })
    }
}
