package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.event.EventActionFailed
import com.yellowtrack.platform.core.data.event.EventsApi
import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.event.AdvanceStationRequest
import com.yellowtrack.platform.core.model.event.CreateEventRequest
import com.yellowtrack.platform.core.model.event.CreatedResponse
import com.yellowtrack.platform.core.model.event.DeliveredResponse
import com.yellowtrack.platform.core.model.event.EventInviteResponse
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.OpenStationRequest
import com.yellowtrack.platform.core.model.event.QrMatrix
import com.yellowtrack.platform.core.model.event.RegistrationSummary
import com.yellowtrack.platform.core.model.event.SittingSummary
import com.yellowtrack.platform.core.model.event.StationSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/**
 * Running an event over HTTP.
 *
 * The refusals are passed through rather than replaced, in the manner of [HttpDocumentSender]
 * and for the same reason: every one of them is something the studio can act on, and the most
 * important — a source already carrying an open station — names the camera in question.
 * Replacing it with "that did not work" would remove the only part a photographer can use.
 */
class HttpEventsApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val credentials: SyncCredentials,
) : EventsApi {
    override suspend fun events(): List<EventSummary> = request { get("$baseUrl/events") { authorised() } }

    override suspend fun createEvent(
        name: String,
        startsAt: Long?,
    ): String =
        request<CreatedResponse> {
            post("$baseUrl/events") {
                authorised()
                contentType(ContentType.Application.Json)
                setBody(CreateEventRequest(name, startsAt))
            }
        }.id

    override suspend fun stations(eventId: String): List<StationSummary> =
        request { get("$baseUrl/events/$eventId/stations") { authorised() } }

    override suspend fun openStation(
        eventId: String,
        name: String,
        sourceKey: String,
    ): String =
        request<CreatedResponse> {
            post("$baseUrl/events/$eventId/stations") {
                authorised()
                contentType(ContentType.Application.Json)
                setBody(OpenStationRequest(name, sourceKey))
            }
        }.id

    override suspend fun closeStation(
        eventId: String,
        stationId: String,
    ) {
        // 204, so there is no body to decode — and nothing to report either way, since a
        // station somebody else already closed is the state being asked for.
        val response =
            send { post("$baseUrl/events/$eventId/stations/$stationId/close") { authorised() } }

        if (!response.isSuccess()) throw EventActionFailed(response.reason())
    }

    override suspend fun invite(eventId: String): EventInviteResponse =
        request { post("$baseUrl/events/$eventId/invite") { authorised() } }

    override suspend fun inviteCode(eventId: String): QrMatrix =
        request { get("$baseUrl/events/$eventId/invite.qr") { authorised() } }

    /** Not JSON, so it is read as text rather than decoded. */
    override suspend fun inviteCard(eventId: String): String {
        val response = send { get("$baseUrl/events/$eventId/invite.html") { authorised() } }

        if (!response.isSuccess()) throw EventActionFailed(response.reason())

        return response.bodyAsText()
    }

    override suspend fun revokeInvite(eventId: String) {
        val response = send { post("$baseUrl/events/$eventId/invite/revoke") { authorised() } }

        if (!response.isSuccess()) throw EventActionFailed(response.reason())
    }

    override suspend fun registrations(eventId: String): List<RegistrationSummary> =
        request { get("$baseUrl/events/$eventId/registrations") { authorised() } }

    override suspend fun advance(
        eventId: String,
        stationId: String,
        registrationId: String,
    ): String =
        request<CreatedResponse> {
            post("$baseUrl/events/$eventId/stations/$stationId/advance") {
                authorised()
                contentType(ContentType.Application.Json)
                setBody(AdvanceStationRequest(registrationId))
            }
        }.id

    override suspend fun sittings(eventId: String): List<SittingSummary> =
        request { get("$baseUrl/events/$eventId/sittings") { authorised() } }

    override suspend fun deliver(
        eventId: String,
        slotId: String,
    ): DeliveredResponse = request { post("$baseUrl/events/$eventId/sittings/$slotId/deliver") { authorised() } }

    // -- Plumbing ---------------------------------------------------------------------------

    private suspend fun HttpRequestBuilder.authorised() {
        bearerAuth(credentials.token() ?: throw EventActionFailed("You are not signed in."))
    }

    private suspend inline fun <reified T> request(crossinline call: suspend HttpClient.() -> HttpResponse): T {
        val response = send(call)

        if (!response.isSuccess()) throw EventActionFailed(response.reason())

        return try {
            response.body()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // A success whose body will not decode means this build and the server disagree
            // about the contract. Said as itself rather than as a network error, because the
            // fix is a deployment and not a better signal.
            throw EventActionFailed("The server sent something this version does not understand.")
        }
    }

    private suspend inline fun send(crossinline call: suspend HttpClient.() -> HttpResponse): HttpResponse =
        try {
            client.call()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: EventActionFailed) {
            // Thrown by `authorised` before anything left the machine. Not a network failure,
            // and must not be relabelled as one.
            throw EventActionFailed("You are not signed in.")
        } catch (_: Throwable) {
            throw EventActionFailed("Could not reach the server.")
        }

    private fun HttpResponse.isSuccess(): Boolean = status.value in 200..299

    private suspend fun HttpResponse.reason(): String =
        runCatching { body<ErrorResponse>().error }
            .getOrElse { "That could not be done (${status.value})." }
}
