package com.yellowtrack.platform.server.event

import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.event.AdvanceStationRequest
import com.yellowtrack.platform.core.model.event.CreateEventRequest
import com.yellowtrack.platform.core.model.event.CreatedResponse
import com.yellowtrack.platform.core.model.event.DeliveredResponse
import com.yellowtrack.platform.core.model.event.EventInviteResponse
import com.yellowtrack.platform.core.model.event.OpenStationRequest
import com.yellowtrack.platform.core.model.event.PhotographAccepted
import com.yellowtrack.platform.core.model.event.PublishRequest
import com.yellowtrack.platform.core.model.event.PublishedResponse
import com.yellowtrack.platform.server.auth.BEARER_AUTH
import com.yellowtrack.platform.server.auth.SessionPrincipal
import com.yellowtrack.platform.server.storage.StoredObjects
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

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
    invites: EventInvites,
    delivery: EventDelivery,
    galleries: EventGalleries,
    /**
     * Where the public sign-up lives.
     *
     * The server builds the URL a QR code encodes rather than the studio's application doing
     * it, so a printed banner and the route that honours it cannot disagree about the
     * address — and so it is one environment variable rather than a constant in four builds.
     */
    photosUrl: String = System.getenv("PHOTOS_URL")?.trimEnd('/') ?: "https://yellowtrackphotos.com",
) {
    route("/events") {
        authenticate(BEARER_AUTH) {
            /** The studio's events, for the list it opens on. */
            get {
                call.respond(events.listEvents(call.studioId()))
            }

            post {
                val request = call.receive<CreateEventRequest>()
                if (request.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("an event needs a name"))
                    return@post
                }

                call.respond(
                    HttpStatusCode.Created,
                    CreatedResponse(events.createEvent(call.studioId(), request.name.trim(), request.startsAt)),
                )
            }

            /** Who has signed up. The list a photographer picks the next name from. */
            get("/{eventId}/registrations") {
                val eventId = call.parameters["eventId"] ?: return@get call.missingEvent()

                call.respond(events.listRegistrations(call.studioId(), eventId))
            }

            /**
             * Seats somebody at a station, which is the act that makes photographs theirs.
             *
             * The join the API was missing: without it no slot ever existed, so every
             * photograph routed to the event's gallery and delivery could never be reached.
             * It failed silently, because routing to the gallery is the correct behaviour
             * when no slot is open — there was simply never a slot.
             *
             * One action rather than a close and an open, as `advanceSlot` explains: the two
             * happening separately is how a photograph lands in the gap between subjects and
             * belongs to neither.
             */
            post("/{eventId}/stations/{stationId}/advance") {
                val stationId =
                    call.parameters["stationId"]
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("which station?"),
                        )
                val request = call.receive<AdvanceStationRequest>()

                val slotId =
                    try {
                        events.advanceSlot(call.studioId(), stationId, request.registrationId)
                    } catch (refused: AdvanceRefused) {
                        call.respond(
                            when (refused) {
                                AdvanceRefused.NoSuchStation,
                                AdvanceRefused.NoSuchRegistration,
                                -> HttpStatusCode.NotFound
                                AdvanceRefused.StationClosed -> HttpStatusCode.Conflict
                            },
                            ErrorResponse(refused.message ?: "That could not be done."),
                        )
                        return@post
                    }

                call.respond(HttpStatusCode.Created, CreatedResponse(slotId))
            }

            /** The sittings, and what each is waiting for. The list the studio works down. */
            get("/{eventId}/sittings") {
                val eventId = call.parameters["eventId"] ?: return@get call.missingEvent()

                call.respond(events.listSittings(call.studioId(), eventId))
            }

            /**
             * Publishes gallery photographs.
             *
             * The studio's decision rather than the camera's — an event is not an unreviewed
             * feed of whatever came off a card.
             */
            post("/{eventId}/photographs/publish") {
                val request = call.receive<PublishRequest>()

                call.respond(PublishedResponse(events.publish(call.studioId(), request.photoIds)))
            }

            /**
             * Hands a sitting to the person in it.
             *
             * A studio action rather than something closing a slot does by itself — ADR 0013,
             * because a mis-advanced slot sends one person's headshot to another and somebody
             * has to have looked.
             */
            post("/{eventId}/sittings/{slotId}/deliver") {
                val slotId =
                    call.parameters["slotId"]
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("which sitting?"),
                        )

                val delivered =
                    try {
                        delivery.deliver(call.studioId(), slotId)
                    } catch (refused: DeliveryRefused) {
                        // The studio's own words back. Every one of these is something it can
                        // act on: close the sitting, add an address, wait for a photograph.
                        call.respond(
                            when (refused) {
                                DeliveryRefused.NoSuchSitting -> HttpStatusCode.NotFound
                                DeliveryRefused.StillOpen,
                                DeliveryRefused.NothingToSend,
                                DeliveryRefused.NoStudioEmail,
                                -> HttpStatusCode.Conflict
                                DeliveryRefused.NotConfigured,
                                DeliveryRefused.Failed,
                                -> HttpStatusCode.ServiceUnavailable
                            },
                            ErrorResponse(refused.message ?: "That could not be sent."),
                        )
                        return@post
                    }

                call.respond(
                    DeliveredResponse(
                        email = delivered.email,
                        photographs = delivered.photographs,
                        sentNow = delivered.sentNow,
                    ),
                )
            }

            /**
             * Stops honouring somebody's gallery link.
             *
             * The remedy when an attendee asks to be forgotten, or when the wrong sitting
             * reached the wrong person — the link is in an inbox and cannot be recalled.
             */
            post("/{eventId}/registrations/{registrationId}/revoke-gallery") {
                val registrationId =
                    call.parameters["registrationId"]
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("which registration?"),
                        )

                galleries.revoke(call.studioId(), registrationId)

                call.respond(HttpStatusCode.NoContent)
            }

            /**
             * The event's invite, issued on first ask and unchanged afterwards.
             *
             * Idempotent because a studio pressing this twice wants one code, and because a
             * second code would silently orphan whichever banner was printed from the first.
             */
            post("/{eventId}/invite") {
                val eventId = call.parameters["eventId"] ?: return@post call.missingEvent()
                val token = invites.issue(call.studioId(), eventId)

                if (token == null) {
                    // No such event *for this studio*. Said as "no such event" rather than
                    // "not yours", because the two answers differ only for somebody probing
                    // for which identifiers exist.
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("There is no such event."))
                    return@post
                }

                call.respond(EventInviteResponse(token = token, url = "$photosUrl/join/$token"))
            }

            /**
             * The same invite, as something a phone can read off a wall.
             *
             * Authenticated, and deliberately so. A printed banner is produced once by the
             * studio, so nothing needs this without a session — and an unauthenticated
             * endpoint that turns an event identifier into a working sign-up code would
             * undo the point of the token.
             *
             * Issues the invite if there is not one yet, so a studio that presses Print
             * before ever pressing anything else gets a code rather than an error.
             */
            get("/{eventId}/invite.svg") {
                val eventId = call.parameters["eventId"] ?: return@get call.missingEvent()
                val token = invites.issue(call.studioId(), eventId)

                if (token == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("There is no such event."))
                    return@get
                }

                call.respondText(
                    QrCode.svg("$photosUrl/join/$token"),
                    ContentType.Image.SVG,
                )
            }

            /**
             * The code as a page a studio prints and puts on a table.
             *
             * Carries the event's name and the link in text as well as the code, because
             * somebody has to know what they are scanning, and a code photographs badly in
             * some lighting while a printed URL can still be typed.
             */
            get("/{eventId}/invite.html") {
                val eventId = call.parameters["eventId"] ?: return@get call.missingEvent()
                val token = invites.issue(call.studioId(), eventId)
                val event = token?.let { invites.lookUp(it) }

                if (token == null || event == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("There is no such event."))
                    return@get
                }

                call.respondText(
                    InviteCard.html(eventName = event.eventName, link = "$photosUrl/join/$token"),
                    ContentType.Text.Html,
                )
            }

            /** Withdraws it. The printed code stops working; the event carries on. */
            post("/{eventId}/invite/revoke") {
                val eventId = call.parameters["eventId"] ?: return@post call.missingEvent()
                invites.revoke(call.studioId(), eventId)

                call.respond(HttpStatusCode.NoContent)
            }

            get("/{eventId}/stations") {
                val eventId = call.parameters["eventId"] ?: return@get call.missingEvent()

                call.respond(events.listStations(call.studioId(), eventId))
            }

            /**
             * Opens a station on a source, or says why it could not.
             *
             * The refusal here is the interesting one: a source already carrying an open
             * station is a 409, not a 500, because it happens to photographers rather than to
             * programmers — two people setting up on the same camera, or a station left open
             * from the morning. The answer names the source so the message can say which.
             */
            post("/{eventId}/stations") {
                val eventId = call.parameters["eventId"] ?: return@post call.missingEvent()
                val request = call.receive<OpenStationRequest>()

                if (request.name.isBlank() || request.sourceKey.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("a station needs a name and a source"),
                    )
                    return@post
                }

                val id =
                    try {
                        events.openStation(
                            studioId = call.studioId(),
                            eventId = eventId,
                            name = request.name.trim(),
                            sourceKey = request.sourceKey.trim(),
                        )
                    } catch (clash: SourceAlreadyInUse) {
                        call.respond(
                            HttpStatusCode.Conflict,
                            ErrorResponse(
                                "A station is already open on ${clash.sourceKey}. Close it before opening another.",
                            ),
                        )
                        return@post
                    }

                call.respond(HttpStatusCode.Created, CreatedResponse(id))
            }

            /**
             * Closing is idempotent, and deliberately says nothing about whether it did
             * anything.
             *
             * A photographer packing up taps this; a second tap, or a tap on a station a
             * colleague already closed, must not read as an error. The station's source
             * returns to the gallery either way, which is the state being asked for.
             */
            post("/{eventId}/stations/{stationId}/close") {
                val stationId =
                    call.parameters["stationId"]
                        ?: return@post call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse("which station?"),
                        )

                events.closeStation(call.studioId(), stationId)

                call.respond(HttpStatusCode.NoContent)
            }

            /**
             * `sourceKey` identifies the watched folder, and through it one camera. It is
             * what stops a second photographer's candids landing in the first one's open
             * slot, so it is required rather than defaulted.
             */
            post("/{eventId}/photographs") {
                val studioId = call.studioId()
                val eventId = call.parameters["eventId"]

                val sourceKey = call.request.queryParameters["source"]
                val capturedAt = call.request.queryParameters["capturedAt"]?.toLongOrNull()

                /*
                 * The client's own clock, so its error can be cancelled.
                 *
                 * `capturedAt` is a file's modification time from a photographer's laptop.
                 * The slot boundaries it is compared against come from this server's clock,
                 * and nothing synchronises the two. The first live run of the walkthrough
                 * lost a photograph to the event's gallery over **39 milliseconds** of skew
                 * — silently, because routing to the gallery is the correct answer when no
                 * slot is open.
                 *
                 * Both directions fail, and they fail differently. A laptop behind the
                 * server drops the opening photographs of a sitting into the gallery, which
                 * is recoverable. A laptop ahead of it matches photographs taken during the
                 * *previous* sitting to the current slot, which delivers one person's
                 * photographs to another — the thing ADR 0013 exists to prevent, arriving
                 * through a clock rather than a mistap.
                 *
                 * Correcting the clock rather than widening the slot is deliberate. A grace
                 * window at the start of a slot would have admitted exactly the photographs
                 * taken while the previous person was still in front of the camera.
                 *
                 * Optional: a client that does not send it is treated as it was before.
                 */
                val clientNow = call.request.queryParameters["clientNow"]?.toLongOrNull()

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

                // Residual error is one-way network delay, which lands the corrected time a
                // few tens of milliseconds *late* — the conservative direction, since a
                // photograph pushed past a slot's end finds no slot and goes to the gallery
                // rather than to the wrong person.
                val correctedCapturedAt = correctForClientClock(capturedAt, clientNow, System.currentTimeMillis())

                val routed =
                    events.recordPhotograph(studioId, eventId, sourceKey, storedObjectId, correctedCapturedAt)

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

private fun ApplicationCall.studioId(): String = principal<SessionPrincipal>()!!.session.studioId

private suspend fun ApplicationCall.missingEvent() = respond(HttpStatusCode.BadRequest, ErrorResponse("which event?"))

/**
 * `capturedAt` as this server's clock would have recorded it.
 *
 * A file's modification time comes from a photographer's laptop; the slot boundaries it is
 * compared against come from here. `clientNow` is read from that same laptop clock as the
 * request is sent, so the difference between it and this server's clock is exactly the error
 * — whatever it is, and without either machine needing to be right.
 *
 * Residual error is one-way network delay, which lands the result a few tens of milliseconds
 * late. That is the conservative direction: a photograph pushed past the end of a slot finds
 * no slot and goes to the gallery, rather than into the next person's sitting.
 *
 * Null [clientNow] means an older client, which is left exactly as it was.
 */
internal fun correctForClientClock(
    capturedAt: Long,
    clientNow: Long?,
    serverNow: Long,
): Long = clientNow?.let { capturedAt + (serverNow - it) } ?: capturedAt
