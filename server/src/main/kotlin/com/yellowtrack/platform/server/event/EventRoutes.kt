package com.yellowtrack.platform.server.event

import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.server.auth.BEARER_AUTH
import com.yellowtrack.platform.server.auth.SessionPrincipal
import com.yellowtrack.platform.server.storage.StoredObjects
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable

/** What became of an uploaded photograph. */
@Serializable
data class PhotographAccepted(
    val photoId: String,
    /** The registration it belongs to, or null when it belongs to the event's gallery. */
    val registrationId: String? = null,
)

/**
 * Where photographs arrive from a photographer's machine.
 *
 * ADR 0013 decision 5: the desktop application watches the folder tethered capture writes
 * to, and posts what appears. This is the other end of that, and it is deliberately dumb —
 * it takes bytes and a time, and asks [Events] whose they are. The client does not get to
 * say which slot a photograph belongs to, because the client is a laptop that may have been
 * asleep and the server is what knows when each slot was open.
 *
 * Authenticated as the studio, not as an attendee. Nothing public happens here.
 */
fun Route.eventRoutes(
    events: Events,
    objects: StoredObjects,
) {
    route("/events") {
        authenticate(BEARER_AUTH) {
            /**
             * `sourceKey` identifies the watched folder, and through it one camera. It is
             * what stops a second photographer's candids landing in the first one's open
             * slot, so it is required rather than defaulted.
             */
            post("/{eventId}/photographs") {
                val studioId = call.principal<SessionPrincipal>()!!.session.studioId
                val eventId = call.parameters["eventId"]

                val sourceKey = call.request.queryParameters["source"]
                val capturedAt = call.request.queryParameters["capturedAt"]?.toLongOrNull()

                if (eventId == null || sourceKey.isNullOrBlank() || capturedAt == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("an event, a source and a capture time are all required"),
                    )
                    return@post
                }

                val bytes = call.receiveChannel().readRemaining().readByteArray()
                if (bytes.isEmpty()) {
                    // A watcher that reads a file while the camera is still writing it sends
                    // nothing rather than a truncated photograph. Refused here so it retries,
                    // instead of being stored as a corrupt object nobody notices until the
                    // studio opens the gallery.
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("that photograph was empty"))
                    return@post
                }

                val contentType = call.request.contentType().takeIf { it != ContentType.Any } ?: ContentType.Image.JPEG

                val storedObjectId =
                    runCatching { objects.store(studioId, contentType.toString(), bytes) }
                        .getOrElse {
                            // Storage refused, so nothing was kept and nothing was routed.
                            // Said plainly, because the watcher's alternative is to drop a
                            // photograph the studio believes it has.
                            call.respond(
                                HttpStatusCode.ServiceUnavailable,
                                ErrorResponse("could not store that photograph: ${it.message ?: "no reason given"}"),
                            )
                            return@post
                        }

                val routed = events.recordPhotograph(studioId, eventId, sourceKey, storedObjectId, capturedAt)

                call.respond(
                    HttpStatusCode.Created,
                    when (routed) {
                        is Routed.ToSlot -> PhotographAccepted(routed.photoId, routed.registrationId)
                        is Routed.ToGallery -> PhotographAccepted(routed.photoId)
                    },
                )
            }
        }
    }
}
