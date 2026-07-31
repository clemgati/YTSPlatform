package com.yellowtrack.platform.server.sync

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncPullResponse
import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.model.sync.SyncPushResponse
import com.yellowtrack.platform.core.model.sync.SyncPushResult
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
                    SyncPullResponse(
                        cursor = changes.cursor,
                        hasMore = changes.hasMore,
                        clients = changes.rows[SyncedEntity.Clients.table].orEmpty().filterIsInstance<Client>(),
                        projects = changes.rows[SyncedEntity.Projects.table].orEmpty().filterIsInstance<Project>(),
                        sessions = changes.rows[SyncedEntity.Sessions.table].orEmpty().filterIsInstance<Session>(),
                        conflicts =
                            changes.rows[SyncedEntity.Conflicts.table].orEmpty().filterIsInstance<SyncConflict>(),
                    ),
                )
            }

            post("/changes") {
                val studioId = call.principal<SessionPrincipal>()!!.session.studioId
                val request = call.receive<SyncPushRequest>()

                // Parents before children, so a project never lands before the client it
                // belongs to and trips a foreign key.
                val results =
                    buildList {
                        request.clients.forEach { add(reconciler.push(studioId, SyncedEntity.Clients, it)) }
                        request.projects.forEach { add(reconciler.push(studioId, SyncedEntity.Projects, it)) }
                        request.sessions.forEach { add(reconciler.push(studioId, SyncedEntity.Sessions, it)) }
                    }

                call.respond(SyncPushResponse(results.map { it.toWire() }))
            }
        }
    }
}

/**
 * The reconciler's own outcome type is internal to the server — it carries `Unanswered`,
 * which is a thing a device concludes rather than a thing a server ever says.
 */
private fun PushResult.toWire() =
    SyncPushResult(
        entityTable = entityTable,
        entityId = entityId,
        outcome =
            when (outcome) {
                PushOutcome.Applied -> SyncPushOutcome.Applied
                PushOutcome.Conflicted -> SyncPushOutcome.Conflicted
                PushOutcome.Rejected -> SyncPushOutcome.Rejected
            },
        version = version,
        detail = detail,
    )
