package com.yellowtrack.platform.server.document

import com.yellowtrack.platform.core.model.auth.EmailAddress
import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.auth.SendDocumentRequest
import com.yellowtrack.platform.server.auth.BEARER_AUTH
import com.yellowtrack.platform.server.auth.SessionPrincipal
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Sending a document to a client.
 *
 * Its own route rather than one under `/auth`, because it is the only thing this server does
 * that sends mail *outwards* — to somebody with no account here — and that difference is the
 * whole of ADR 0011.
 */
fun Route.documentRoutes(mail: DocumentMail?) {
    route("/documents") {
        authenticate(BEARER_AUTH) {
            post("/send") {
                val studioId = call.principal<SessionPrincipal>()!!.session.studioId
                val request = call.receive<SendDocumentRequest>()

                if (mail == null) {
                    call.respond(HttpStatusCode.NotImplemented, ErrorResponse("this server cannot send mail"))
                    return@post
                }

                // The same rule sign-up uses, and for a better reason: a malformed address
                // here is a bounce against a reputation every studio shares.
                if (!EmailAddress.isPlausible(request.to)) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("that does not look like an email address"))
                    return@post
                }

                if (request.html.isBlank() || request.subject.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("a document and a subject are required"))
                    return@post
                }

                try {
                    mail.send(
                        studioId = studioId,
                        to = request.to.trim(),
                        subject = request.subject.trim(),
                        html = request.html,
                        text = request.text,
                    )
                    call.respond(HttpStatusCode.NoContent)
                } catch (refused: SendRefused) {
                    // Said in the studio's own words rather than hidden. ADR 0011 decision 6:
                    // the account-existence rule that silences a failed reset does not apply
                    // to an address the studio typed for a client it deals with.
                    val status =
                        when (refused) {
                            is SendRefused.TooMany -> HttpStatusCode.TooManyRequests
                            is SendRefused.NoStudioEmail -> HttpStatusCode.BadRequest
                            else -> HttpStatusCode.BadGateway
                        }

                    call.respond(status, ErrorResponse(refused.message ?: "That could not be sent."))
                }
            }
        }
    }
}
