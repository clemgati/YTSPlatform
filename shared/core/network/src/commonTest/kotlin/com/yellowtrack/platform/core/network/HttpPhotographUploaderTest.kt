package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.event.UploadOutcome
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
import kotlin.test.assertTrue

/**
 * Which answers mean "put this photograph down" and which mean "try again".
 *
 * This is the only place that decision is made, and it is asymmetric on purpose. Calling a
 * temporary failure permanent loses a photograph that cannot be retaken, because the guest
 * has gone home. Calling a permanent failure temporary costs a retry loop, which is visible
 * and annoying and loses nothing. So anything not clearly permanent is temporary.
 */
class HttpPhotographUploaderTest {
    @Test
    fun `a stored photograph comes back with its identifier`() =
        runTest {
            val outcome =
                uploader {
                    respond(
                        """{"photoId":"photo-1","registrationId":"reg-1"}""",
                        HttpStatusCode.Created,
                        headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }.send()

            assertEquals(UploadOutcome.Stored("photo-1"), outcome)
        }

    /** The server's own word for "this file will never work". */
    @Test
    fun `a bad request is a refusal that carries the reason`() =
        runTest {
            val outcome =
                uploader {
                    respond(
                        """{"error":"that photograph was empty"}""",
                        HttpStatusCode.BadRequest,
                        headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }.send()

            assertEquals(UploadOutcome.Refused("that photograph was empty"), outcome)
        }

    /**
     * The status the server chose deliberately for a missing bucket.
     *
     * If this were read as a refusal, an outage lasting a minute would silently discard
     * every photograph taken during it.
     */
    @Test
    fun `storage being unavailable keeps the photograph`() =
        runTest {
            val outcome = uploader { respondError(HttpStatusCode.ServiceUnavailable) }.send()

            assertTrue(outcome is UploadOutcome.Unavailable, "a 503 must not discard the file: $outcome")
        }

    /**
     * The trap in the middle of the range.
     *
     * 401 is a client error by number and not one by meaning — it says nothing about the
     * photograph, only that this device needs to sign in again. Reading it by its category
     * would throw away everything shot during a lapsed session.
     */
    @Test
    fun `an expired session keeps the photograph`() =
        runTest {
            val unauthorised = uploader { respondError(HttpStatusCode.Unauthorized) }.send()
            val forbidden = uploader { respondError(HttpStatusCode.Forbidden) }.send()

            assertTrue(unauthorised is UploadOutcome.Unavailable, "401 must not discard the file: $unauthorised")
            assertTrue(forbidden is UploadOutcome.Unavailable, "403 must not discard the file: $forbidden")
        }

    /** Also in the 4xx range, and also a request to wait rather than a verdict. */
    @Test
    fun `being asked to slow down keeps the photograph`() =
        runTest {
            val outcome = uploader { respondError(HttpStatusCode.TooManyRequests) }.send()

            assertTrue(outcome is UploadOutcome.Unavailable, "429 must not discard the file: $outcome")
        }

    /** Nothing reached the server, so nothing is known about the photograph. */
    @Test
    fun `an unreachable server keeps the photograph`() =
        runTest {
            val outcome = uploader { throw RuntimeException("connection reset by peer") }.send()

            assertTrue(outcome is UploadOutcome.Unavailable, "a dead network must not discard the file: $outcome")
        }

    /**
     * Signing in may still be in progress during a launch, and photographs may already be
     * landing in the folder. Waiting is right; discarding them is not.
     */
    @Test
    fun `no token yet keeps the photograph`() =
        runTest {
            val outcome = uploader(token = null) { respondError(HttpStatusCode.OK) }.send()

            assertTrue(outcome is UploadOutcome.Unavailable, "a device still signing in must not discard: $outcome")
        }

    /**
     * A status nobody anticipated — a proxy's 418, a gateway's own idea of an error.
     *
     * Unrecognised means unknown, and the cheaper mistake with an unknown is to keep the
     * file.
     */
    @Test
    fun `an unrecognised answer keeps the photograph`() =
        runTest {
            val outcome = uploader { respondError(HttpStatusCode.fromValue(599)) }.send()

            assertTrue(outcome is UploadOutcome.Unavailable, "an unknown status must not discard the file: $outcome")
        }

    /** What the watcher actually puts on the wire, since the server routes on it. */
    @Test
    fun `the source and capture time travel as query parameters`() =
        runTest {
            var url = ""
            val engine =
                MockEngine { request ->
                    url = request.url.toString()
                    respond(
                        """{"photoId":"photo-1"}""",
                        HttpStatusCode.Created,
                        headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }

            HttpPhotographUploader(
                HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } },
                "https://api.example.invalid",
                { "a-token" },
            ).upload("event-1", "Camera A", 1_234_567, "DSC_0001.JPG", "image/jpeg", byteArrayOf(1, 2, 3))

            assertTrue("/events/event-1/photographs" in url, url)
            assertTrue("source=Camera" in url, "the source must reach the server: $url")
            assertTrue("capturedAt=1234567" in url, "the capture time must reach the server: $url")
        }

    // -- Fixtures ---------------------------------------------------------------------------

    private fun uploader(
        token: String? = "a-token",
        handler: MockScope.() -> io.ktor.client.request.HttpResponseData,
    ): HttpPhotographUploader {
        val client =
            HttpClient(MockEngine { handler() }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

        return HttpPhotographUploader(client, "https://api.example.invalid", { token })
    }

    private suspend fun HttpPhotographUploader.send() =
        upload("event-1", "Camera A", 1_000, "DSC_0001.JPG", "image/jpeg", byteArrayOf(1, 2, 3))
}

private typealias MockScope = io.ktor.client.engine.mock.MockRequestHandleScope
