package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.server.account.AccountDeletion
import com.yellowtrack.platform.server.account.StudioExport
import com.yellowtrack.platform.server.auth.Accounts
import com.yellowtrack.platform.server.auth.BEARER_AUTH
import com.yellowtrack.platform.server.auth.PasswordResets
import com.yellowtrack.platform.server.auth.SessionPrincipal
import com.yellowtrack.platform.server.auth.authRoutes
import com.yellowtrack.platform.server.mail.MailConfig
import com.yellowtrack.platform.server.mail.MailHealth
import com.yellowtrack.platform.server.mail.MonitoredMailer
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.hours

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

/**
 * How often deleted studios are looked for.
 *
 * Daily. The window is measured in days, so checking more often would only move a purge a
 * few hours earlier than a studio was told to expect it.
 */
private val PURGE_INTERVAL = 24.hours

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

    // Wrapped rather than watched from outside, so what /ready reports is what actually
    // happened on the socket rather than what the environment said at boot.
    val mailHealth = MailHealth()
    val resets =
        PasswordResets(
            database,
            mailConfig?.let { MonitoredMailer(SmtpMail(it), mailHealth) },
            onSendFailure = { log.error("could not send mail", it) },
        )

    val deletion = AccountDeletion(database, AccountDeletion.retentionFromEnvironment())

    // Deletion is a promise with a date on it, and a promise nothing ever runs is a way of
    // keeping data somebody asked to be rid of. In the server rather than a systemd timer
    // because the purge needs the entity registry to know which tables a studio owns and in
    // what order they can be removed — a shell script would need that list written out a
    // second time, and a second list is one that goes stale.
    launch {
        while (isActive) {
            // Delayed first, so a server restarting in a loop does not purge on every boot.
            delay(PURGE_INTERVAL)
            runCatching { deletion.purge() }
                .onSuccess { report ->
                    if (!report.isEmpty) log.info("purged ${report.studios} deleted studios (${report.rows} rows)")
                }
                // Logged rather than fatal. A purge that cannot run is a promise slipping,
                // not a reason to stop serving the studios still working.
                .onFailure { log.error("could not purge deleted studios", it) }
        }
    }

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
                    mailError = mailHealth.lastFailure,
                    mailLastSucceededAt = mailHealth.lastSucceededAt,
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

        authRoutes(accounts, resets, StudioExport(database), deletion)
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
    /**
     * Why the last attempt to send mail failed, or null if it worked.
     *
     * [mail] says a host is configured; this says what happened when one was last used.
     * The two disagree in exactly the case worth catching — configured and refusing — which
     * is where a wrong credential, an unverified sender and a sandboxed region all land.
     *
     * Only SMTP-level failures. A bounce happens after SES has accepted the message and is
     * invisible from here.
     */
    val mailError: String? = null,
    /**
     * When a send last worked, or null if none has *in this process*.
     *
     * Null is not a failure and must not be read as one. It means unproved: a server that
     * has just restarted has sent nothing, and a deployment where nobody has needed a
     * password reset can sit here for weeks while mail is perfectly healthy. It separates
     * "working" from "never tried", which `mail` alone cannot.
     */
    val mailLastSucceededAt: Long? = null,
)
