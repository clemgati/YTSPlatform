package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.server.auth.Accounts
import com.yellowtrack.platform.server.auth.BEARER_AUTH
import com.yellowtrack.platform.server.auth.PasswordResets
import com.yellowtrack.platform.server.auth.SessionPrincipal
import com.yellowtrack.platform.server.auth.authRoutes
import com.yellowtrack.platform.server.mail.MailConfig
import com.yellowtrack.platform.server.mail.SmtpMail
import com.yellowtrack.platform.server.sync.Reconciler
import com.yellowtrack.platform.server.sync.syncRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

/**
 * The API in front of Postgres.
 *
 * Clients cannot reach the database directly — there is no Postgres driver for iOS or
 * wasm, and exposing a database to client applications would be wrong regardless. See
 * `docs/adr/0007-ktor-server-over-cloud-postgres.md`.
 *
 * What is here so far: a health route, and the authentication that everything else will
 * sit behind. The business endpoints arrive with synchronisation.
 */
fun main() {
    // Schema first: a pool handing out connections to a database the code does not match
    // is a slower way of failing.
    migrate()
    val database = Database.pooled()

    embeddedServer(
        factory = Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT,
        host = "127.0.0.1",
        module = { module(database) },
    ).start(wait = true)
}

/**
 * Bound to the loopback address on purpose: Apache terminates TLS and proxies to
 * localhost, so the JAR has no business listening on a public interface.
 */
private const val DEFAULT_PORT = 8080

fun Application.module(database: Database) {
    val accounts = Accounts(database)

    // Null when MAIL_HOST is unset. Reset codes are still issued and stored — they simply
    // are not delivered, which a development machine can live with and a deployment cannot,
    // so it is said once at boot rather than discovered at the first reset.
    val mailConfig = MailConfig.fromEnvironment()
    if (mailConfig == null) log.warn("MAIL_HOST is not set: password reset codes will be issued but not sent.")
    val resets =
        PasswordResets(database, mailConfig?.let(::SmtpMail), onSendFailure = { log.error("could not send mail", it) })

    install(ContentNegotiation) {
        json(apiJson)
    }

    install(StatusPages) {
        // A malformed body is the caller's mistake, and the default would report it as
        // the server's. Nothing else is translated: an unexpected exception should be a
        // 500 and a stack trace in the log, not a tidy message that hides a bug.
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("that request body could not be read"))
        }
    }

    install(Authentication) {
        bearer(BEARER_AUTH) {
            realm = "Yellow Track"
            authenticate { credential ->
                accounts.authenticate(credential.token)?.let(::SessionPrincipal)
            }
        }
    }

    routing {
        get("/health") {
            call.respond(Health(status = "ok"))
        }

        authRoutes(accounts, resets)
        syncRoutes(Reconciler(database))
    }
}

/** Answers the proxy's health check, and nothing more. */
@Serializable
data class Health(
    val status: String,
)
