package com.yellowtrack.platform.server.event

import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.event.GalleryResponse
import com.yellowtrack.platform.core.model.event.InvitedEventResponse
import com.yellowtrack.platform.core.model.event.SignUpToEventRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * The only routes in this application that anybody may call.
 *
 * Everything else is behind a bearer token belonging to a studio. These two are reached by
 * somebody who has just pointed a phone at a printed code, and who will never have an account
 * here (ADR 0013 decision 7).
 *
 * Three properties are load-bearing, and each is a way this could leak if written the obvious
 * way.
 *
 * **An unknown token and a withdrawn one answer identically.** Both are 404. Distinguishing
 * them would confirm that a particular code once existed.
 *
 * **Signing up answers the same whether or not the address was already there.** Otherwise
 * this becomes a way of asking whether a named person attended a named event, which is
 * precisely the kind of question a photograph delivery service must not answer.
 *
 * **The event's name is all that comes back.** Not its photograph count, not its stations,
 * not the studio's other events.
 */
fun Route.inviteRoutes(
    invites: EventInvites,
    galleries: EventGalleries,
) {
    /**
     * Somebody's own photographs, behind the token that was mailed to them.
     *
     * Unknown, withdrawn, and nothing-released-yet all answer identically. Telling them apart
     * would let a stranger holding a forwarded link learn whether a particular person had
     * been photographed.
     */
    get("/gallery/{token}") {
        val gallery = call.parameters["token"]?.let { galleries.photographs(it) }

        if (gallery == null) {
            call.respond(HttpStatusCode.NotFound, ErrorResponse("These photographs are not available."))
            return@get
        }

        call.respond(GalleryResponse(eventName = gallery.eventName, photographs = gallery.photographs))
    }

    route("/join/{token}") {
        get {
            val token = call.parameters["token"]
            val invited = token?.let { invites.lookUp(it) }

            if (invited == null) {
                // The same answer for a token that never existed, a token that was withdrawn,
                // and an event that has since been deleted.
                call.respond(HttpStatusCode.NotFound, ErrorResponse("This sign-up is not open."))
                return@get
            }

            call.respond(InvitedEventResponse(eventName = invited.eventName))
        }

        post {
            val token = call.parameters["token"]
            val request = call.receive<SignUpToEventRequest>()

            // Not `token?.let { ... } ?: NoSuchInvite`. `signUp` returns null to mean
            // *success*, so the elvis cannot tell that apart from a missing token — and the
            // first version of this reported every successful sign-up as "not open".
            if (token == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("This sign-up is not open."))
                return@post
            }

            when (val refusal = invites.signUp(token, request.email, request.name)) {
                null ->
                    // No body. There is nothing to tell somebody about their own sign-up that
                    // they did not just type, and a registration identifier is something the
                    // studio's side binds photographs to rather than anything a guest needs.
                    call.respond(HttpStatusCode.NoContent)

                SignUpRefused.NoSuchInvite ->
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("This sign-up is not open."))

                SignUpRefused.BadAddress ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("That does not look like an email address."),
                    )

                SignUpRefused.TooManyForNow ->
                    // 429, and about the event rather than about the person holding the phone.
                    call.respond(
                        HttpStatusCode.TooManyRequests,
                        ErrorResponse("This event is taking a lot of sign-ups. Please try again in a few minutes."),
                    )
            }
        }
    }
}
