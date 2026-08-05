package com.yellowtrack.platform.server.auth

import com.yellowtrack.platform.core.model.auth.AccountResponse
import com.yellowtrack.platform.core.model.auth.DeleteAccountRequest
import com.yellowtrack.platform.core.model.auth.DeleteAccountResponse
import com.yellowtrack.platform.core.model.auth.EmailAddress
import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.auth.ForgotPasswordRequest
import com.yellowtrack.platform.core.model.auth.PendingDeletionResponse
import com.yellowtrack.platform.core.model.auth.ResetPasswordRequest
import com.yellowtrack.platform.core.model.auth.RestoreAccountRequest
import com.yellowtrack.platform.core.model.auth.SessionResponse
import com.yellowtrack.platform.core.model.auth.SignInRequest
import com.yellowtrack.platform.core.model.auth.SignUpRequest
import com.yellowtrack.platform.server.account.AccountDeletion
import com.yellowtrack.platform.server.account.StudioExport
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** The signed-in caller, carried from the bearer token to the handler. */
data class SessionPrincipal(
    val session: AuthenticatedSession,
)

/**
 * Sign-up, sign-in, sign-out, and who am I.
 *
 * The refusals are deliberately uninformative. A wrong password and an unknown address
 * give the same 401 with the same body, because distinguishing them turns this into a way
 * to ask whether a given photographer has an account here.
 */
fun Route.authRoutes(
    accounts: Accounts,
    resets: PasswordResets,
    export: StudioExport? = null,
    deletion: AccountDeletion? = null,
) {
    route("/auth") {
        post("/sign-up") {
            val request = call.receive<SignUpRequest>()

            val invalid = validate(request)
            if (invalid != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(invalid))
                return@post
            }

            try {
                val signedIn =
                    accounts.signUp(
                        email = request.email,
                        password = request.password,
                        name = request.name,
                        studioName = request.studioName,
                    )
                call.respond(HttpStatusCode.Created, signedIn.toResponse())
            } catch (_: EmailAlreadyRegistered) {
                // The one place an address is confirmed to exist, and unavoidable: the
                // person signing up has to be told why it did not work.
                call.respond(HttpStatusCode.Conflict, ErrorResponse("that email address already has an account"))
            }
        }

        post("/sign-in") {
            val request = call.receive<SignInRequest>()

            accounts
                .signIn(request.email, request.password)
                .onSuccess { call.respond(HttpStatusCode.OK, it.toResponse()) }
                .onFailure { failure ->
                    // 403 rather than 401: the credentials were accepted and the state of
                    // the studio is what stands in the way, which is a different thing for a
                    // client to act on — one offers a password reset, the other offers the
                    // studio back.
                    //
                    // Not 409, though it fits the meaning better. Sign-up already answers 409
                    // for an address that is taken, and one status carrying two meanings
                    // makes the client tell them apart by sniffing the body — which works
                    // until a message changes.
                    when (val reason = (failure as? SignInRefused)?.failure) {
                        is SignInFailure.PendingDeletion ->
                            call.respond(
                                HttpStatusCode.Forbidden,
                                PendingDeletionResponse(
                                    purgeAfter = reason.purgeAfter,
                                    error = "This studio is waiting to be deleted. You can still restore it.",
                                ),
                            )
                        else ->
                            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("email or password is wrong"))
                    }
                }
        }

        /**
         * Brings a deleted studio back.
         *
         * Unauthenticated in the bearer sense and password-checked instead, because the
         * deletion revoked every session — a studio in the window has no token to present,
         * and requiring one would make the window unusable by the only person it is for.
         */
        post("/restore-account") {
            val request = call.receive<RestoreAccountRequest>()

            accounts
                .restore(request.email, request.password)
                .onSuccess { call.respond(HttpStatusCode.OK, it.toResponse()) }
                .onFailure {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("email or password is wrong"))
                }
        }

        // Always 202, always the same body. ADR 0010 decision 3: any difference between a
        // known and an unknown address is a difference somebody can measure, and sign-in
        // was deliberately built not to answer that question.
        post("/forgot-password") {
            val request = call.receive<ForgotPasswordRequest>()
            resets.request(request.email)
            call.respond(
                HttpStatusCode.Accepted,
                ErrorResponse("If that address has an account, a code is on its way."),
            )
        }

        post("/reset-password") {
            val request = call.receive<ResetPasswordRequest>()

            if (request.newPassword.length < MINIMUM_PASSWORD_LENGTH) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("a password needs at least $MINIMUM_PASSWORD_LENGTH characters"),
                )
                return@post
            }

            try {
                resets.reset(request.email, request.code.trim().uppercase(), request.newPassword)
                call.respond(HttpStatusCode.NoContent)
            } catch (_: ResetRefused) {
                // One message for expired, consumed, superseded and never-existed. Telling
                // them apart tells a stranger which codes were real.
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("That code is not usable. Ask for a new one."))
            }
        }

        authenticate(BEARER_AUTH) {
            post("/sign-out") {
                accounts.signOut(call.principal<SessionPrincipal>()!!.session.sessionId)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/me") {
                val session = call.principal<SessionPrincipal>()!!.session

                when (val whoami = accounts.whoami(session)) {
                    // The session resolved, but the account or studio behind it has since
                    // been deleted. Answering 401 rather than 500: the token is no longer
                    // good for anything, and that is what the caller needs to act on.
                    null -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse("that session is no longer valid"))
                    else ->
                        call.respond(
                            AccountResponse(
                                accountId = whoami.account.id,
                                email = whoami.account.email,
                                name = whoami.account.name,
                                studioId = whoami.studioId,
                                studioName = whoami.studioName,
                            ),
                        )
                }
            }

            /**
             * The whole studio, as a file it can keep.
             *
             * Under `/auth` beside `/me` because it is answered for whoever the token is,
             * and takes no parameters for the same reason: the studio is the session's, not
             * something a caller may name. There is no path by which one studio asks for
             * another's, which is the only property that matters here.
             */
            get("/export") {
                val session = call.principal<SessionPrincipal>()!!.session

                when (export) {
                    null -> call.respond(HttpStatusCode.NotImplemented, ErrorResponse("export is not configured"))
                    else -> {
                        // An attachment, so a browser saves it rather than rendering a few
                        // megabytes of JSON into a tab. The file carries its own
                        // `exportedAt`, which survives being renamed on the way to a disk
                        // in a way a date in the filename does not.
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            "attachment; filename=\"yellowtrack-export.json\"",
                        )
                        call.respond(export.of(session.studioId, session.accountId))
                    }
                }
            }

            /**
             * Deletes the studio and everything in it.
             *
             * A POST rather than a DELETE on `/auth/account`, because it carries a body: the
             * password is checked again here, and a DELETE with a body is the kind of thing
             * proxies and clients disagree about.
             */
            post("/delete-account") {
                val session = call.principal<SessionPrincipal>()!!.session
                val request = call.receive<DeleteAccountRequest>()

                when (deletion) {
                    null ->
                        call.respond(HttpStatusCode.NotImplemented, ErrorResponse("deletion is not configured"))
                    else ->
                        when (val done = deletion.request(session.accountId, session.studioId, request.password)) {
                            // The same answer a wrong password gets anywhere else here, and
                            // for the same reason it is not more specific.
                            null ->
                                call.respond(
                                    HttpStatusCode.Unauthorized,
                                    ErrorResponse("that password is wrong"),
                                )
                            else -> call.respond(HttpStatusCode.OK, DeleteAccountResponse(done.purgeAfter))
                        }
                }
            }
        }
    }
}

private fun SignedIn.toResponse() =
    SessionResponse(
        token = token,
        expiresAt = expiresAt,
        accountId = account.id,
        email = account.email,
        name = account.name,
        studioId = studioId,
        studioName = studioName,
    )

/**
 * The minimum that keeps an unusable account out of the database.
 *
 * A twelve-character floor rather than a composition rule: length is what actually
 * resists guessing, and demanding a digit and a symbol mostly produces `Password1!`.
 */
private fun validate(request: SignUpRequest): String? =
    when {
        // Shape only, and shared with the clients so the two cannot disagree. It cannot
        // tell whether the domain exists, which is the fault that actually happens — see
        // EmailAddress.suggestion, which is offered in the form rather than enforced here.
        !EmailAddress.isPlausible(request.email) -> "that does not look like an email address"
        request.password.length < MINIMUM_PASSWORD_LENGTH ->
            "a password needs at least $MINIMUM_PASSWORD_LENGTH characters"
        request.name.isBlank() -> "a name is required"
        request.studioName.isBlank() -> "a studio name is required"
        else -> null
    }

private const val MINIMUM_PASSWORD_LENGTH = 12

const val BEARER_AUTH = "bearer"
