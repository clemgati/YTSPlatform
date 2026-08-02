package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.auth.AuthFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Which failure a studio is told about when signing in does not work.
 *
 * The distinction is the whole point. "That email or password is not right" sends somebody
 * round a password reset; "could not reach the server" sends them to look at their signal;
 * "something went wrong here" sends them nowhere at all. Two deployments in one afternoon
 * were diagnosed slowly because a healthy server produced the last of those.
 */
class HttpAuthApiFailureTest {
    @Test
    fun `a transport failure is unreachable, not a mystery`() =
        runTest {
            // Thrown as a Throwable rather than an Exception, which is how a browser
            // refusing a request on its own account can arrive. `catch (_: Exception)` let
            // this escape, and the studio was told the fault was on their device.
            val api = api { throw NotAnException() }

            assertFailsWith<AuthFailure.Unreachable> { api.signIn("ada@harbourline.test", "hunter2") }
        }

    @Test
    fun `cancelling a sign-in is not a failed sign-in`() =
        runTest {
            val api = api { throw CancellationException("navigated away") }

            // Swallowing this reported a connection failure to somebody who had simply
            // moved on, and left the coroutine looking as though it had finished.
            assertFailsWith<CancellationException> { api.signIn("ada@harbourline.test", "hunter2") }
        }

    @Test
    fun `an answer this app cannot read says so, and points at the server's version`() =
        runTest {
            val api =
                api {
                    respond(
                        content = """{"unexpected":"shape"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }

            val failure = assertFailsWith<AuthFailure.Rejected> { api.signIn("ada@harbourline.test", "hunter2") }

            assertTrue(
                failure.reason.contains("older version"),
                "a body the client cannot parse is usually version skew, and saying so is the " +
                    "difference between redeploying and hunting a device bug: ${failure.reason}",
            )
        }

    @Test
    fun `a refusal from the server is still a refusal`() =
        runTest {
            val api = api { respond(content = "", status = HttpStatusCode.Unauthorized) }

            assertFailsWith<AuthFailure.BadCredentials> { api.signIn("ada@harbourline.test", "hunter2") }
        }

    @Test
    fun `a good answer is read`() =
        runTest {
            val api =
                api {
                    respond(
                        content =
                            """
                            {"token":"a-token","expiresAt":9999999999999,"accountId":"account-1",
                             "email":"ada@harbourline.test","name":"Ada Okafor",
                             "studioId":"studio-1","studioName":"Harbourline Photography"}
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }

            assertEquals("a-token", api.signIn("ada@harbourline.test", "hunter2").token)
        }

    private class NotAnException : Throwable("a failure that is not an Exception")

    private fun api(handler: MockRequestHandlerScope.() -> io.ktor.client.request.HttpResponseData): HttpAuthApi {
        val client =
            HttpClient(MockEngine { handler() }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

        return HttpAuthApi(client, "https://api.example.invalid")
    }
}

private typealias MockRequestHandlerScope = io.ktor.client.engine.mock.MockRequestHandleScope
