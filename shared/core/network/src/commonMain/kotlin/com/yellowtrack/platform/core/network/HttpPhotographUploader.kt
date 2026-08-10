package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.event.PhotographUploader
import com.yellowtrack.platform.core.data.event.UploadOutcome
import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.event.PhotographAccepted
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Sending one photograph, and — the whole job — deciding what its answer means.
 *
 * [UploadOutcome.Refused] and [UploadOutcome.Unavailable] are opposite instructions to the
 * watcher: put the file down forever, or keep it and try again. This class is where an HTTP
 * status becomes one of them, so getting the mapping wrong here either loses photographs or
 * loops on one until the event ends.
 */
class HttpPhotographUploader(
    private val client: HttpClient,
    private val baseUrl: String,
    private val credentials: SyncCredentials,
    private val clock: AppClock = AppClock.System,
) : PhotographUploader {
    override suspend fun upload(
        eventId: String,
        sourceKey: String,
        capturedAt: Long,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): UploadOutcome {
        // Not signed in *yet* is the usual reason during a launch, and it is temporary. A
        // photograph must not be discarded because the session was still being restored.
        val token = credentials.token() ?: return UploadOutcome.Unavailable("this device is not signed in")

        val response =
            try {
                client.post("$baseUrl/events/$eventId/photographs") {
                    bearerAuth(token)
                    parameter("source", sourceKey)
                    parameter("capturedAt", capturedAt)
                    // This machine's clock, alongside the time it claims the shutter fired.
                    // The server subtracts one from the other to cancel however wrong this
                    // laptop is — see the upload route. Read as late as possible, so the
                    // only error left is one-way network delay.
                    parameter("clientNow", clock.now().toEpochMilliseconds())
                    this.contentType(ContentType.parse(contentType))
                    setBody(bytes)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                // Never reached the server, so nothing is known about the photograph and the
                // only safe reading is "not yet".
                return UploadOutcome.Unavailable(failure.message ?: "could not reach the server")
            }

        return when {
            response.status.isSuccess() ->
                UploadOutcome.Stored(
                    runCatching { response.body<PhotographAccepted>().photoId }.getOrElse { "" },
                )

            // A token that has expired or been revoked. Textually a client error, but not one
            // about *this photograph* — signing in again fixes it, and the file is still
            // good. Refusing it here would throw away every photograph taken during a lapsed
            // session.
            response.status == HttpStatusCode.Unauthorized ||
                response.status == HttpStatusCode.Forbidden ->
                UploadOutcome.Unavailable("this device is no longer signed in")

            // Rate limiting is a request to wait, not a verdict on the file.
            response.status == HttpStatusCode.TooManyRequests ->
                UploadOutcome.Unavailable("the server asked this device to slow down")

            response.status.value in 400..499 -> UploadOutcome.Refused(response.reason())

            // 5xx and anything unrecognised. Unrecognised lands here deliberately: the
            // expensive mistake is discarding a photograph, so anything not understood is
            // treated as temporary.
            else -> UploadOutcome.Unavailable(response.reason())
        }
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    private suspend fun HttpResponse.reason(): String =
        runCatching { body<ErrorResponse>().error }
            .getOrElse { "the server answered ${status.value}" }
}
