package com.yellowtrack.platform.server.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/**
 * The wire shapes of the authentication endpoints.
 *
 * Defined here rather than in `core:model` on purpose. `core:model` holds the domain — the
 * things a photography business is made of — and ADR 0008 already worried about it
 * acquiring concerns that belong to how data moves rather than what it is. When the client
 * wiring lands these move to `core:network`, which `docs/ARCHITECTURE_V2.md` has been
 * holding a place for.
 */
@Serializable
data class SignUpRequest(
    val email: String,
    val password: String,
    val name: String,
    val studioName: String,
)

@Serializable
data class SignInRequest(
    val email: String,
    val password: String,
)

@Serializable
data class SessionResponse(
    val token: String,
    val expiresAt: Long,
    val accountId: String,
    val email: String,
    val name: String,
    val studioId: String,
    val studioName: String,
)

@Serializable
data class AccountResponse(
    val accountId: String,
    val email: String,
    val name: String,
    val studioId: String,
    val studioName: String,
)

@Serializable
data class ErrorResponse(
    val error: String,
)

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
fun Route.authRoutes(accounts: Accounts) {
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
                .onFailure { call.respond(HttpStatusCode.Unauthorized, ErrorResponse("email or password is wrong")) }
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
        !request.email.contains('@') -> "that does not look like an email address"
        request.password.length < MINIMUM_PASSWORD_LENGTH ->
            "a password needs at least $MINIMUM_PASSWORD_LENGTH characters"
        request.name.isBlank() -> "a name is required"
        request.studioName.isBlank() -> "a studio name is required"
        else -> null
    }

private const val MINIMUM_PASSWORD_LENGTH = 12

const val BEARER_AUTH = "bearer"
