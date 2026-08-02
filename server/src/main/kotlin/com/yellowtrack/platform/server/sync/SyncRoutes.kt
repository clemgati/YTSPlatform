package com.yellowtrack.platform.server.sync

import com.yellowtrack.platform.core.model.auth.ErrorResponse
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientContactLink
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.invoice.Invoice
import com.yellowtrack.platform.core.model.invoice.Payment
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.quote.Quote
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncPullResponse
import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.model.sync.SyncPushResponse
import com.yellowtrack.platform.core.model.sync.SyncPushResult
import com.yellowtrack.platform.server.auth.BEARER_AUTH
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
                        contacts = changes.rows[SyncedEntity.Contacts.table].orEmpty().filterIsInstance<Contact>(),
                        clientContactLinks =
                            changes.rows[SyncedEntity.ClientContactLinks.table]
                                .orEmpty()
                                .filterIsInstance<ClientContactLink>(),
                        projects = changes.rows[SyncedEntity.Projects.table].orEmpty().filterIsInstance<Project>(),
                        sessions = changes.rows[SyncedEntity.Sessions.table].orEmpty().filterIsInstance<Session>(),
                        invoices = changes.rows[SyncedEntity.Invoices.table].orEmpty().filterIsInstance<Invoice>(),
                        payments = changes.rows[SyncedEntity.Payments.table].orEmpty().filterIsInstance<Payment>(),
                        crewMembers =
                            changes.rows[SyncedEntity.CrewMembers.table].orEmpty().filterIsInstance<CrewMember>(),
                        deliverables =
                            changes.rows[SyncedEntity.Deliverables.table].orEmpty().filterIsInstance<Deliverable>(),
                        gearItems =
                            changes.rows[SyncedEntity.GearItems.table].orEmpty().filterIsInstance<GearItem>(),
                        packingEntries =
                            changes.rows[SyncedEntity.PackingEntries.table].orEmpty().filterIsInstance<PackingEntry>(),
                        storageVolumes =
                            changes.rows[SyncedEntity.StorageVolumes.table].orEmpty().filterIsInstance<StorageVolume>(),
                        mediaCopies =
                            changes.rows[SyncedEntity.MediaCopies.table].orEmpty().filterIsInstance<MediaCopy>(),
                        leads = changes.rows[SyncedEntity.Leads.table].orEmpty().filterIsInstance<Lead>(),
                        expenses = changes.rows[SyncedEntity.Expenses.table].orEmpty().filterIsInstance<Expense>(),
                        mileages = changes.rows[SyncedEntity.Mileages.table].orEmpty().filterIsInstance<Mileage>(),
                        quotes = changes.rows[SyncedEntity.Quotes.table].orEmpty().filterIsInstance<Quote>(),
                        contracts =
                            changes.rows[SyncedEntity.Contracts.table].orEmpty().filterIsInstance<Contract>(),
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
                        // Parents before children, for the same reason SyncedEntity.all is
                        // ordered: a link applied before its client or contact exists fails a
                        // foreign key, and a studio's first sync is exactly when both are new.
                        request.clients.forEach { add(reconciler.push(studioId, SyncedEntity.Clients, it)) }
                        request.contacts.forEach { add(reconciler.push(studioId, SyncedEntity.Contacts, it)) }
                        request.clientContactLinks.forEach {
                            add(reconciler.push(studioId, SyncedEntity.ClientContactLinks, it))
                        }
                        request.projects.forEach { add(reconciler.push(studioId, SyncedEntity.Projects, it)) }
                        request.sessions.forEach { add(reconciler.push(studioId, SyncedEntity.Sessions, it)) }
                        request.invoices.forEach { add(reconciler.push(studioId, SyncedEntity.Invoices, it)) }
                        request.crewMembers.forEach { add(reconciler.push(studioId, SyncedEntity.CrewMembers, it)) }
                        request.deliverables.forEach { add(reconciler.push(studioId, SyncedEntity.Deliverables, it)) }
                        request.gearItems.forEach { add(reconciler.push(studioId, SyncedEntity.GearItems, it)) }
                        request.storageVolumes.forEach {
                            add(reconciler.push(studioId, SyncedEntity.StorageVolumes, it))
                        }
                        request.packingEntries.forEach {
                            add(reconciler.push(studioId, SyncedEntity.PackingEntries, it))
                        }
                        request.mediaCopies.forEach { add(reconciler.push(studioId, SyncedEntity.MediaCopies, it)) }
                        request.leads.forEach { add(reconciler.push(studioId, SyncedEntity.Leads, it)) }
                        request.expenses.forEach { add(reconciler.push(studioId, SyncedEntity.Expenses, it)) }
                        request.mileages.forEach { add(reconciler.push(studioId, SyncedEntity.Mileages, it)) }
                        request.quotes.forEach { add(reconciler.push(studioId, SyncedEntity.Quotes, it)) }
                        request.contracts.forEach { add(reconciler.push(studioId, SyncedEntity.Contracts, it)) }
                        request.payments.forEach { add(reconciler.push(studioId, SyncedEntity.Payments, it)) }
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
