package com.yellowtrack.platform.server.sync

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.server.auth.BEARER_AUTH
import com.yellowtrack.platform.server.auth.ErrorResponse
import com.yellowtrack.platform.server.auth.SessionPrincipal
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
 * What a device asks for, and what it sends back.
 *
 * Three typed lists rather than one list of opaque payloads. A generic envelope would
 * extend to the remaining eighteen entities without a code change, and would throw away
 * the only thing ADR 0007 bought: `core:model` compiled into both sides, so that adding a
 * field to `Session` is a compile error here rather than a field that quietly stops
 * crossing. Growing this by one list per entity is the cost of keeping that, and it is
 * the cheaper mistake to make.
 */
@Serializable
data class PullResponse(
    /** Where the device should resume. Unchanged when nothing came back. */
    val cursor: Long,
    /** Whether the studio has more beyond this page, so the device knows to come again. */
    val hasMore: Boolean,
    val clients: List<Client> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
)

@Serializable
data class PushRequest(
    val clients: List<Client> = emptyList(),
    val projects: List<Project> = emptyList(),
    val sessions: List<Session> = emptyList(),
)

@Serializable
data class PushResultResponse(
    val entityTable: String,
    val entityId: String,
    val outcome: String,
    val version: Int,
    val detail: String? = null,
)

@Serializable
data class PushResponse(
    val results: List<PushResultResponse>,
) {
    /** So a device can show "three of your changes were also made elsewhere" without counting. */
    val conflicted: Int get() = results.count { it.outcome == PushOutcome.Conflicted.name }
}

/**
 * Pull and push.
 *
 * Both are behind bearer authentication and act as the studio the token was issued for —
 * never as a studio named in the request. A device that could choose which studio it was
 * syncing would make row level security a formality.
 */
fun Route.syncRoutes(reconciler: Reconciler) {
    authenticate(BEARER_AUTH) {
        route("/sync") {
            get("/changes") {
                val studioId = call.principal<SessionPrincipal>()!!.session.studioId

                val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
                if (since < 0) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("a cursor cannot be negative"))
                    return@get
                }

                val limit =
                    call.request.queryParameters["limit"]
                        ?.toIntOrNull()
                        ?.coerceIn(1, Reconciler.DEFAULT_PAGE)
                        ?: Reconciler.DEFAULT_PAGE

                val changes = reconciler.pull(studioId, since, limit)

                call.respond(
                    PullResponse(
                        cursor = changes.cursor,
                        hasMore = changes.hasMore,
                        clients = changes.rows[SyncedEntity.Clients.table].orEmpty().filterIsInstance<Client>(),
                        projects = changes.rows[SyncedEntity.Projects.table].orEmpty().filterIsInstance<Project>(),
                        sessions = changes.rows[SyncedEntity.Sessions.table].orEmpty().filterIsInstance<Session>(),
                    ),
                )
            }

            post("/changes") {
                val studioId = call.principal<SessionPrincipal>()!!.session.studioId
                val request = call.receive<PushRequest>()

                // Parents before children, so a project never lands before the client it
                // belongs to and trips a foreign key.
                val results =
                    buildList {
                        request.clients.forEach { add(reconciler.push(studioId, SyncedEntity.Clients, it)) }
                        request.projects.forEach { add(reconciler.push(studioId, SyncedEntity.Projects, it)) }
                        request.sessions.forEach { add(reconciler.push(studioId, SyncedEntity.Sessions, it)) }
                    }

                call.respond(PushResponse(results.map { it.toResponse() }))
            }
        }
    }
}

private fun PushResult.toResponse() =
    PushResultResponse(
        entityTable = entityTable,
        entityId = entityId,
        outcome = outcome.name,
        version = version,
        detail = detail,
    )
