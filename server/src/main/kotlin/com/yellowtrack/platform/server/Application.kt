package com.yellowtrack.platform.server

import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.server.account.AccountDeletion
import com.yellowtrack.platform.server.account.StudioExport
import com.yellowtrack.platform.server.auth.Accounts
import com.yellowtrack.platform.server.auth.BEARER_AUTH
import com.yellowtrack.platform.server.auth.PasswordResets
import com.yellowtrack.platform.server.auth.SessionPrincipal
import com.yellowtrack.platform.server.auth.authRoutes
import com.yellowtrack.platform.server.document.DocumentMail
import com.yellowtrack.platform.server.document.documentRoutes
import com.yellowtrack.platform.server.event.EventDelivery
import com.yellowtrack.platform.server.event.EventGalleries
import com.yellowtrack.platform.server.event.EventInvites
import com.yellowtrack.platform.server.event.Events
import com.yellowtrack.platform.server.event.eventRoutes
import com.yellowtrack.platform.server.event.inviteRoutes
import com.yellowtrack.platform.server.event.publicSite
import com.yellowtrack.platform.server.mail.MailConfig
import com.yellowtrack.platform.server.mail.MailHealth
import com.yellowtrack.platform.server.mail.MailNotifications
import com.yellowtrack.platform.server.mail.MonitoredMailer
import com.yellowtrack.platform.server.mail.SmtpMail
import com.yellowtrack.platform.server.mail.sesNotificationRoutes
import com.yellowtrack.platform.server.storage.ObjectStore
import com.yellowtrack.platform.server.storage.S3ObjectStore
import com.yellowtrack.platform.server.storage.StorageConfig
import com.yellowtrack.platform.server.storage.StoredObjects
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
    /**
     * Where photographs go, when something other than the environment should decide.
     *
     * Only the tests pass this, and they need it: without a bucket the upload route answers
     * 503 before it ever reaches routing, so everything past that point — which slot a
     * photograph belongs to, and the clock correction that decides it — was unreachable in
     * process and could only be checked by uploading to real S3.
     */
    objects: ObjectStore? = null,
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

    // Its own sender, and its own health story. A document goes outwards to somebody with no
    // account here, which is a different act from a reset — ADR 0011.
    val documentFrom = DocumentMail.fromEnvironment()
    if (documentFrom == null) log.warn("DOCUMENT_FROM is not set: a studio cannot email a document to its client.")
    val documentMail =
        DocumentMail(
            database = database,
            mailer = mailConfig?.let { MonitoredMailer(SmtpMail(it), mailHealth) },
            fromAddress = documentFrom,
            onSendFailure = { log.error("could not send a document", it) },
        )

    // What SES says *after* it has accepted a message, which is the half neither the SMTP
    // conversation nor the 202 can see. Null topic means the route refuses everything: a
    // signature proves an AWS customer sent it, not that the customer was us.
    val sesTopicArn = System.getenv("SES_TOPIC_ARN")?.takeIf { it.isNotBlank() }
    if (sesTopicArn == null) {
        log.warn("SES_TOPIC_ARN is not set: bounces and deliveries will not be recorded. See docs/DEPLOYMENT.md.")
    }
    val mailNotifications = MailNotifications(database)

    // Photographs, if this deployment has anywhere to put them. Absent is a normal state:
    // nothing uploads yet, and a purge with no bucket has no objects to remove.
    val storage = StorageConfig.fromEnvironment()
    if (storage == null) {
        log.info("STORAGE_BUCKET is not set: this deployment cannot store photographs.")
    }
    @Suppress("NAME_SHADOWING")
    val objects = objects ?: storage?.let { S3ObjectStore(it) } ?: ObjectStore.Unconfigured

    val deletion = AccountDeletion(database, AccountDeletion.retentionFromEnvironment(), objects = objects)

    // Events, and the register that lets the purge reach what they store.
    val events = Events(database)
    val storedObjects = StoredObjects(database, objects)
    val invites = EventInvites(database, events)
    val galleries = EventGalleries(database, objects)

    // Its own sender, and on the domain the link points at.
    //
    // EVENTS_FROM rather than DOCUMENT_FROM: a guest sees `yellowtrackphotos.com` in the
    // sender and in the link, which is one name rather than two, and a document to a client
    // has no reason to leave from the photograph-delivery domain. Falls back to DOCUMENT_FROM
    // so a deployment that has not verified the second domain in SES still delivers.
    val eventsFrom = System.getenv("EVENTS_FROM")?.takeIf { it.isNotBlank() } ?: documentFrom
    if (System.getenv("EVENTS_FROM").isNullOrBlank()) {
        log.info("EVENTS_FROM is not set: event deliveries will leave from DOCUMENT_FROM.")
    }

    val delivery =
        EventDelivery(
            database = database,
            mailer = mailConfig?.let { MonitoredMailer(SmtpMail(it), mailHealth) },
            fromAddress = eventsFrom,
            onSendFailure = { log.error("could not deliver a sitting", it) },
        )

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

    /*
     * Only installed when there is something to allow. The native clients send no Origin and
     * need none of this; the browser build cannot work without its own origin listed, and
     * listing none is safer than listing all.
     *
     * The public site's own origin, which is not optional and was not there.
     *
     * A browser sends `Origin` on every POST, including a same-origin one. So the sign-up
     * page — served by this application, from this application — had its own form submission
     * refused as cross-origin, and every guest saw a failure. Nothing caught it: curl sends
     * no `Origin`, so `walk-event.py` and every probe in this repository passed.
     *
     * Ktor's same-origin detection does not rescue it either. Apache terminates TLS, so the
     * request arrives as plain HTTP on a loopback socket while `Origin` says `https://…` —
     * the schemes disagree and the check fails.
     *
     * Derived from PHOTOS_URL rather than added to ALLOWED_ORIGINS by hand, because this
     * application serves those pages and therefore already knows where they live. A
     * deployment cannot forget it.
     */
    val photosOrigin = (System.getenv("PHOTOS_URL")?.trimEnd('/') ?: "https://yellowtrackphotos.com")
    val corsOrigins = (deployment.allowedOrigins + photosOrigin).distinct()

    if (deployment.allowedOrigins.isNotEmpty()) {
        install(CORS) {
            corsOrigins.forEach { origin ->
                val withoutScheme = origin.substringAfter("://")
                allowHost(
                    withoutScheme,
                    schemes = listOf(origin.substringBefore("://")),
                    // `www` is a different origin to a browser, and both hosts serve these
                    // pages. A guest who reached the sign-up page by typing the address with
                    // a `www` on it loaded it, filled it in, and was refused — the same
                    // failure this block exists to fix, still live on the host half the world
                    // types. Found by the walkthrough against production.
                    //
                    // Named rather than a wildcard: `www` of the site this application
                    // serves, and no other subdomain of it.
                    subDomains = listOf("www"),
                )
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

            // Null rather than fatal when the database is unreachable: this endpoint's job
            // is to report that, and a delivery summary that throws would replace the
            // diagnosis with a 500.
            val delivery = runCatching { mailNotifications.summary() }.getOrNull()

            val readiness =
                Readiness(
                    database = reached.isSuccess,
                    mail = mailConfig != null,
                    mailError = mailHealth.lastFailure,
                    mailLastSucceededAt = mailHealth.lastSucceededAt,
                    mailLastDeliveredAt = delivery?.lastDeliveredAt,
                    mailRecentBounces = delivery?.recentBounces ?: 0,
                    mailRecentComplaints = delivery?.recentComplaints ?: 0,
                    // Postgres says "permission denied to set role" here, which names the
                    // problem exactly, so it is worth forwarding rather than swallowing.
                    //
                    // That is only safe because the deployment restricts this endpoint to
                    // the instance — a `<Location /ready>` block in the Apache vhost, since
                    // `ProxyPass /` would otherwise answer it to anyone. The text names
                    // roles and hosts. If that block is ever dropped, this line starts
                    // disclosing them; see `docs/DEPLOYMENT.md` under Apache.
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
        documentRoutes(documentMail)
        // Unauthenticated by necessity — Amazon posts here with no token of ours. The
        // signature and the topic check are the authentication; see the route.
        sesNotificationRoutes(mailNotifications, sesTopicArn)
        eventRoutes(events, storedObjects, invites, delivery, galleries)

        // Public. No `authenticate` around it, deliberately and uniquely — see InviteRoutes.
        inviteRoutes(invites, galleries)

        // The two pages themselves, at the addresses printed on banners and mailed to guests.
        publicSite()
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
     * reported by [mailRecentBounces] instead.
     */
    val mailError: String? = null,
    /**
     * When a send last worked, or null if none has *in this process*.
     *
     * Null is not a failure and must not be read as one. It means unproved: a server that
     * has just restarted has sent nothing, and a deployment where nobody has needed a
     * password reset can sit here for weeks while mail is perfectly healthy. It separates
     * "working" from "never tried", which `mail` alone cannot.
     *
     * "Worked" here means SMTP accepted it. Whether anybody received it is
     * [mailLastDeliveredAt], and the gap between the two is where a mistyped domain lives.
     */
    val mailLastSucceededAt: Long? = null,
    /**
     * When SES last confirmed a message was *delivered*, or null if it never has.
     *
     * The stronger claim, and the one that used to be impossible to make. [mailLastSucceededAt]
     * says this server handed a message over; this says it reached a mailbox. Survives a
     * restart, because it is a fact about a message rather than about this process.
     *
     * Null while [mailLastSucceededAt] is set means one of two things: nothing has been
     * delivered yet, or the SNS subscription is not wired up. `SES_TOPIC_ARN` being unset is
     * the first thing to check.
     */
    val mailLastDeliveredAt: Long? = null,
    /**
     * Bounces in the last seven days.
     *
     * SES holds the account to a bounce rate under 5% and suspends sending above it, and the
     * first sign used to be resets no longer arriving for anybody. A number here is not
     * automatically a fault — one permanent bounce is somebody who mistyped their address —
     * but a number that climbs is the account's reputation being spent.
     */
    val mailRecentBounces: Int = 0,
    /**
     * Complaints in the last seven days.
     *
     * Held to a far tighter rate than bounces — under 0.1% — so this mattering does not need
     * it to be large. Any complaint at all against a server that only sends password resets
     * and documents a studio asked it to send is worth reading.
     */
    val mailRecentComplaints: Int = 0,
)
