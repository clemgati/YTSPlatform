package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.sync.SyncTransport
import com.yellowtrack.platform.core.model.sync.SyncPullResponse
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.model.sync.SyncPushResponse
import com.yellowtrack.platform.core.model.sync.SyncPushResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Raised when the server refused the whole request rather than individual rows.
 *
 * Distinct from a row being rejected, which comes back inside a normal response and is the
 * device's business to record. This means the exchange did not happen, so the outbox keeps
 * everything and the next attempt tries again.
 */
class SyncRequestFailed(
    val status: Int,
    override val message: String,
) : Exception(message)

/** Raised when the session is no longer good, so the caller can send the studio to sign in. */
class SyncUnauthorised : Exception("this device is no longer signed in")

/**
 * Supplies the bearer token for the signed-in device.
 *
 * A function rather than a stored string, because a token can be replaced — by a
 * re-authentication, or by signing out — while this transport is alive, and reading it per
 * request means the transport never holds a stale one.
 *
 * Where the token is *kept* is deliberately not decided here. Each platform has a different
 * right answer — Keychain, EncryptedSharedPreferences, an OS keyring — and none of them
 * belong in the thing that makes HTTP requests.
 */
fun interface SyncCredentials {
    suspend fun token(): String?
}

/**
 * The real transport: the device talking to the Ktor server.
 *
 * Thin on purpose. Everything that decides whether synchronisation is *correct* — ordering,
 * conflict handling, when the cursor advances — lives in `SyncEngine`, which is tested
 * without a network. What is left here is the part a network can get wrong: reaching the
 * server, proving who is asking, and turning a failure into something the caller can act on.
 *
 * The envelopes are `core:model`'s, so this is compiled against the same contract the
 * server's routes are. That is the whole reason ADR 0007 chose a Kotlin server, and it only
 * pays off if both sides use the shared types rather than their own copies — which they did
 * until these were unified.
 */
class HttpSyncTransport(
    private val client: HttpClient,
    private val baseUrl: String,
    private val credentials: SyncCredentials,
) : SyncTransport {
    override suspend fun pull(
        since: Long,
        limit: Int,
    ): SyncPullResponse {
        val response =
            client.get("$baseUrl/sync/changes") {
                authorise()
                parameter("since", since)
                parameter("limit", limit)
            }

        return response.orFail("could not fetch changes").body()
    }

    override suspend fun push(changes: SyncPushRequest): List<SyncPushResult> {
        val response =
            client.post("$baseUrl/sync/changes") {
                authorise()
                contentType(ContentType.Application.Json)
                setBody(changes)
            }

        return response.orFail("could not upload changes").body<SyncPushResponse>().results
    }

    private suspend fun io.ktor.client.request.HttpRequestBuilder.authorise() {
        val token = credentials.token() ?: throw SyncUnauthorised()
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    /**
     * Turns a failed exchange into an exception rather than an empty result.
     *
     * A pull that answered 500 and is read as "nothing has changed" would advance nothing
     * and look like a quiet, successful sync — which is the failure mode this whole feature
     * is written against. Throwing is what makes the drain keep its outbox entries and the
     * cursor stay where it was.
     */
    private suspend fun HttpResponse.orFail(context: String): HttpResponse =
        when {
            status.isSuccess() -> this
            status == HttpStatusCode.Unauthorized -> throw SyncUnauthorised()
            else -> throw SyncRequestFailed(status.value, "$context: the server answered ${status.value}")
        }
}
