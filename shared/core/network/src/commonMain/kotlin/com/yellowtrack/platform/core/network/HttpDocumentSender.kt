package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.document.DocumentSender
import com.yellowtrack.platform.core.data.document.SendFailed
import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.auth.SendDocumentRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Sending a document over HTTP.
 *
 * The refusals are passed through rather than replaced. Unlike sign-in, every one of them is
 * something the studio can act on — a missing studio email, a daily limit, a mail server that
 * would not take the message — and replacing them with "that did not work" would hide the
 * only useful part.
 */
class HttpDocumentSender(
    private val client: HttpClient,
    private val baseUrl: String,
    private val credentials: SyncCredentials,
) : DocumentSender {
    override suspend fun send(
        to: String,
        subject: String,
        html: String,
        text: String,
    ) {
        val token = credentials.token() ?: throw SendFailed("You are not signed in.")

        val response =
            try {
                client.post("$baseUrl/documents/send") {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(SendDocumentRequest(to = to, subject = subject, html = html, text = text))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                throw SendFailed("Could not reach the server. Nothing has been sent.")
            }

        if (!response.status.isSuccess()) throw SendFailed(response.reason())
    }

    private suspend fun HttpResponse.reason(): String =
        runCatching { body<ErrorResponse>().error }
            .getOrElse { "That could not be sent (${status.value})." }
}
