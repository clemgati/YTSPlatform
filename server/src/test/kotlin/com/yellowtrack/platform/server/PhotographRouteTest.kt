package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
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
import kotlin.test.assertTrue

/**
 * The upload endpoint over HTTP, as the desktop watcher would meet it.
 *
 * The interesting half is the refusals. A folder watcher runs unattended on a photographer's
 * laptop for the length of an event, so what it does when something is wrong is decided
 * entirely by the status code it gets back — and the two mistakes that cost a photograph are
 * a truncated file accepted as if it were whole, and a storage outage reported as a client
 * error the watcher will never retry.
 *
 * Storage is deliberately left unconfigured here. `module()` reads STORAGE_BUCKET from the
 * environment and the test environment has none, so [com.yellowtrack.platform.server.storage.ObjectStore.Unconfigured]
 * is what the route gets — which is exactly the deployment this endpoint has to fail politely on.
 */
class PhotographRouteTest {
    /**
     * Nothing about this endpoint is public. It takes a studio's photographs, and an
     * unauthenticated caller could otherwise fill somebody else's event with anything.
     */
    @Test
    fun `an upload with no token is refused`() =
        withServer { client ->
            val response =
                client.post("/events/any-event/photographs?source=Camera%20A&capturedAt=1") {
                    contentType(ContentType.Image.JPEG)
                    setBody(byteArrayOf(1, 2, 3))
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status, response.bodyAsText())
        }

    /**
     * The source is what binds a photograph to one camera, and through that to whichever
     * station is open on it. Guessing a default would quietly put a second photographer's
     * candids into the first one's sitting, so it is required.
     */
    @Test
    fun `an upload with no source is refused`() =
        withServer { client ->
            val session = client.signUp()

            val response =
                client.post("/events/any-event/photographs?capturedAt=1") {
                    bearerAuth(session.token)
                    contentType(ContentType.Image.JPEG)
                    setBody(byteArrayOf(1, 2, 3))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        }

    /**
     * A capture time is not the arrival time.
     *
     * The server routes by when the shutter fired, because a laptop that lost its network for
     * ten minutes delivers a backlog whose photographs belong to slots that have since closed.
     * Defaulting to "now" would file every one of them under whoever is sitting down at the
     * moment the connection came back.
     */
    @Test
    fun `an upload with no capture time is refused`() =
        withServer { client ->
            val session = client.signUp()

            val response =
                client.post("/events/any-event/photographs?source=Camera%20A") {
                    bearerAuth(session.token)
                    contentType(ContentType.Image.JPEG)
                    setBody(byteArrayOf(1, 2, 3))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
        }

    /**
     * The truncated-file case.
     *
     * A watcher that opens a file while the camera is still writing it can read nothing at
     * all. Storing that would put a corrupt object in a gallery and a row saying it is fine,
     * which nobody discovers until a client opens it. Refused instead, so the watcher tries
     * again once the camera has finished.
     *
     * This also holds the ordering inside the handler: the emptiness check comes before
     * storage is touched, so an empty upload is a 400 even on a deployment whose bucket is
     * missing — which would otherwise answer 503 and send the watcher into a retry loop over
     * a file that will never have any bytes.
     */
    @Test
    fun `an empty photograph is refused rather than stored`() =
        withServer { client ->
            val session = client.signUp()

            val response =
                client.post("/events/any-event/photographs?source=Camera%20A&capturedAt=1") {
                    bearerAuth(session.token)
                    contentType(ContentType.Image.JPEG)
                    setBody(ByteArray(0))
                }

            assertEquals(HttpStatusCode.BadRequest, response.status, response.bodyAsText())
            assertTrue("empty" in response.bodyAsText(), "should say what was wrong: ${response.bodyAsText()}")
        }

    /**
     * Storage being unreachable is the server's problem, and it has to say so.
     *
     * 503 rather than 500, and rather than any 4xx: the watcher's whole decision is whether
     * the photograph is worth keeping and sending again. A 4xx says "this will never work",
     * and a photograph deleted from a card on that advice is gone. This is the status code
     * that makes the difference between a retry and a loss.
     */
    @Test
    fun `an upload with no storage configured is refused as unavailable, leaving nothing behind`() =
        withServer { client ->
            val session = client.signUp()

            val response =
                client.post("/events/any-event/photographs?source=Camera%20A&capturedAt=1") {
                    bearerAuth(session.token)
                    contentType(ContentType.Image.JPEG)
                    setBody(byteArrayOf(1, 2, 3))
                }

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status, response.bodyAsText())
            // And the row that was written first was taken away again, so a studio that could
            // not store a photograph is not left holding a record of one it does not have.
            assertEquals(0, storedObjectsFor(session.studioId), "a failed upload must leave no row")
        }

    // -- Plumbing -----------------------------------------------------------------------

    private fun withServer(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(TestDatabase.database) }
            block(client)
        }

    private suspend fun HttpClient.signUp(): SessionResponse {
        val email = "watcher-${counter++}-${System.nanoTime()}@harbourline.test"
        val response =
            post("/auth/sign-up") {
                contentType(ContentType.Application.Json)
                setBody(
                    apiJson.encodeToString(
                        SignUpRequest(email, "a long enough password", "Ada Okafor", "Harbourline Photography"),
                    ),
                )
            }

        assertEquals(HttpStatusCode.Created, response.status, "sign-up should have succeeded: ${response.bodyAsText()}")

        return apiJson.decodeFromString(response.bodyAsText())
    }

    private fun storedObjectsFor(studioId: String): Int =
        TestDatabase.connection().use { connection ->
            connection.prepareStatement("SELECT count(*) FROM stored_object WHERE studio_id = ?").use { statement ->
                statement.setString(1, studioId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    private companion object {
        private var counter = 0
    }
}
