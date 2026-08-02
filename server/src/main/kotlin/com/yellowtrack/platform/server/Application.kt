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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
import io.ktor.server.plugins.cors.routing.CORS
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
    //
    // As the owner, not as the role that serves requests — see DatabaseConfig.forMigrations.
    migrate(DatabaseConfig.forMigrations())
    val database = Database.pooled()

    embeddedServer(
        factory = Netty,
        port = System.getenv("PORT")?.toIntOrNull() ?: DEFAULT_PORT,
        host = "127.0.0.1",
        module = { module(database, Deployment.fromEnvironment()) },
    ).start(wait = true)
}

/**
 * Bound to the loopback address on purpose: Apache terminates TLS and proxies to
 * localhost, so the JAR has no business listening on a public interface.
 */
private const val DEFAULT_PORT = 8080

fun Application.module(
    database: Database,
    deployment: Deployment = Deployment(allowedOrigins = emptyList()),
) {
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

    // Only installed when there is something to allow. The native clients send no Origin
    // and need none of this; the browser build cannot work without its own origin listed,
    // and listing none is safer than listing all.
    if (deployment.allowedOrigins.isNotEmpty()) {
        install(CORS) {
            deployment.allowedOrigins.forEach { origin ->
                val withoutScheme = origin.substringAfter("://")
                allowHost(withoutScheme, schemes = listOf(origin.substringBefore("://")))
            }
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
        }
    } else {
        log.info(
            "ALLOWED_ORIGINS is not set: the browser build will be refused by the browser. Native clients are unaffected.",
        )
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
        // Answers whether the process is up. Deliberately touches nothing: a health check
        // that queries the database turns a slow database into an unhealthy process and a
        // proxy that takes it out of service.
        get("/health") {
            call.respond(Health(status = "ok"))
        }

        // Answers whether it can actually do its job. Separate from health because the
        // remedies differ: a failing readiness check is a misconfiguration to fix, not a
        // process to restart.
        get("/ready") {
            val reached =
                runCatching { database.unscoped { it.createStatement().use { s -> s.execute("SELECT 1") } } }

            val readiness =
                Readiness(
                    database = reached.isSuccess,
                    mail = mailConfig != null,
                    // Postgres says "permission denied to set role" here, which names the
                    // problem exactly. Worth forwarding: this endpoint is reachable only
                    // from the instance, so there is no one to disclose it to.
                    databaseError =
                        reached
                            .exceptionOrNull()
                            ?.message
                            ?.trim()
                            ?.take(300),
                )

            call.respond(
                if (readiness.database) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                readiness,
            )
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

/**
 * Whether the server can reach what it needs.
 *
 * `mail` being false is reported rather than fatal: reset codes are still issued, they
 * simply are not delivered. On a laptop that is survivable; in a deployment it means a
 * password reset that answers "a code is on its way" and never arrives, which is the one
 * failure ADR 0010 deliberately made invisible to the caller.
 */
@Serializable
data class Readiness(
    val database: Boolean,
    val mail: Boolean,
    /**
     * Why the database is unreachable, when it is.
     *
     * A bare `"database": false` costs an hour: it looks like a network or credentials
     * problem, and the commonest cause is neither. Every transaction issues
     * `SET LOCAL ROLE yellowtrack_app`, which requires membership in that role — so a
     * server still on the owner's credentials after a first boot fails here while its
     * migrations succeeded, and says nothing about which of the two it was.
     */
    val databaseError: String? = null,
)
