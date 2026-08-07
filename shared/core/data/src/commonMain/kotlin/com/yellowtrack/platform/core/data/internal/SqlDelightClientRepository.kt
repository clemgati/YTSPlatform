package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.database.YellowTrackDatabase
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactLink
import com.yellowtrack.platform.core.model.client.ClientContactLinkId
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import com.yellowtrack.platform.core.database.Client as ClientRow

internal class SqlDelightClientRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
    private val remote: RemoteWriter,
) : DatabaseBackedRepository(provider),
    ClientRepository {
    private val studioId get() = studioContext.studioId.value

    private fun instant(millis: Long) = Instant.fromEpochMilliseconds(millis)

    override fun observeClients(): Flow<List<Client>> =
        observing { db ->
            db.clientQueries
                .selectAll(studioId)
                .asListFlow(dispatcher)
                .withContacts(db)
        }

    override fun observeClient(clientId: ClientId): Flow<Client?> =
        observing { db ->
            db.clientQueries
                .selectById(clientId.value)
                .asListFlow(dispatcher)
                .withContacts(db)
                .map(List<Client>::firstOrNull)
        }

    override fun searchClients(query: String): Flow<List<Client>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeClients()

        return observing { db ->
            db.clientQueries
                .search(studioId, "%${trimmed.escapeLikeWildcards()}%")
                .asListFlow(dispatcher)
                .withContacts(db)
        }
    }

    override suspend fun getClient(clientId: ClientId): Client? = observeClient(clientId).first()

    override suspend fun saveClient(client: Client) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Decided before anything is written, because the server has to be told the whole
        // change at once and a link's id is minted here rather than by the database. Doing
        // it the other way round — write locally, then send what happened — is the shape
        // ADR 0012 removes.
        val plan = db.planContacts(client, now)

        // One request. A client whose contacts arrived and whose links did not is a client
        // that displays with nobody attached, so they travel together or not at all.
        remote.write(
            SyncPushRequest(
                clients = listOf(client),
                contacts = plan.contacts,
                clientContactLinks = plan.attach + plan.retire,
            ),
        )

        db.transaction {
            db.clientQueries.insertOrIgnore(
                id = client.id.value,
                studio_id = client.studioId.value,
                account_name = client.accountName,
                account_type = client.accountType.name,
                notes = client.notes,
                tags = encodeTags(client.tags),
                created_at = client.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = client.audit.deletedAt.toEpochMillisOrNull(),
                version = client.audit.version.toLong(),
            )

            db.clientQueries.update(
                accountName = client.accountName,
                accountType = client.accountType.name,
                notes = client.notes,
                tags = encodeTags(client.tags),
                updatedAt = now,
                deletedAt = client.audit.deletedAt.toEpochMillisOrNull(),
                version = client.audit.version.toLong(),
                id = client.id.value,
            )

            db.applyContactPlan(plan, now)
        }
    }

    override suspend fun deleteClient(clientId: ClientId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // A delete travels as the row carrying a tombstone, so it is read first.
        val existing = getClient(clientId) ?: return

        // Each link is retired individually, because a peer learns a link is gone from the
        // link's own tombstone. Retiring them in bulk and sending only the client would
        // delete them here and leave them attached everywhere else.
        val links =
            db.clientQueries
                .selectLiveClientContactLinks(clientId.value)
                .awaitAsList()
                .map { it.toDomain().let { link -> link.copy(audit = link.audit.deleted(instant(now))) } }

        remote.write(
            SyncPushRequest(
                clients = listOf(existing.copy(audit = existing.audit.deleted(instant(now)))),
                clientContactLinks = links,
            ),
        )

        db.transaction {
            links.forEach { db.clientQueries.softDeleteClientContactById(deletedAt = now, id = it.id.value) }
            db.clientQueries.softDelete(deletedAt = now, id = clientId.value)
        }
    }

    /**
     * What a save is going to change, worked out before anything is written.
     *
     * Split out of `reconcileContacts` when clients moved to writing through the server.
     * The two halves used to be one pass that decided and wrote in the same step, which is
     * fine when the outbox carries the news afterwards and impossible when the server has
     * to be told first: the request has to name every row, and a new link's id is minted
     * here rather than by the database.
     */
    private data class ContactPlan(
        val contacts: List<Contact>,
        val attach: List<ClientContactLink>,
        val retire: List<ClientContactLink>,
    )

    /**
     * Reads only. Works out which contacts differ, which links are new, and which are gone.
     *
     * Touching only what changed is the property being preserved here. Retiring every link
     * and reviving the survivors was simpler and was fine while links stayed on the device;
     * once they synchronise, it bumps every link's version twice per save, re-uploads
     * contacts nobody edited, and makes two devices collide over links neither touched.
     *
     * A link is identified by the contact and role it joins, because that is what the caller
     * can express — it passes `ClientContact`, which has no link id. An existing row's id is
     * never regenerated: it is what a second device knows the row by.
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun YellowTrackDatabase.planContacts(
        client: Client,
        now: Long,
    ): ContactPlan {
        val existing = clientQueries.selectLiveClientContactLinks(client.id.value).awaitAsList()
        val wantedKeys = client.contacts.map { it.contact.id.value to it.role.name }.toSet()
        val byKey = existing.associateBy { it.contact_id to it.role }

        val retire =
            existing
                .filter { (it.contact_id to it.role) !in wantedKeys }
                .map { row -> row.toDomain().let { it.copy(audit = it.audit.deleted(instant(now))) } }

        val contacts = mutableListOf<Contact>()
        val attach = mutableListOf<ClientContactLink>()

        client.contacts.forEach { link ->
            if (differsFromStored(link.contact)) contacts += link.contact

            if ((link.contact.id.value to link.role.name) in byKey) return@forEach

            attach +=
                ClientContactLink(
                    id = ClientContactLinkId.new(),
                    studioId = client.studioId,
                    clientId = client.id,
                    contactId = link.contact.id,
                    role = link.role,
                    audit = AuditMetadata.createdAt(instant(now)),
                )
        }

        return ContactPlan(contacts = contacts, attach = attach, retire = retire)
    }

    /**
     * Writes exactly what [planContacts] decided, and nothing it did not.
     *
     * Deliberately does no reading of its own. Anything it re-derived could differ from what
     * was sent a moment ago, and then the server and this device would hold different rows
     * while both believed the write succeeded.
     */
    private suspend fun YellowTrackDatabase.applyContactPlan(
        plan: ContactPlan,
        now: Long,
    ) {
        plan.contacts.forEach { writeContact(it, now) }

        plan.retire.forEach { clientQueries.softDeleteClientContactById(deletedAt = now, id = it.id.value) }

        plan.attach.forEach { link ->
            // The unique index on (client_id, contact_id, role) means an insert racing a
            // revived tombstone is ignored rather than duplicated.
            clientQueries.insertOrIgnoreClientContact(
                id = link.id.value,
                studio_id = link.studioId.value,
                client_id = link.clientId.value,
                contact_id = link.contactId.value,
                role = link.role.name,
                created_at = now,
                updated_at = now,
                deleted_at = null,
                version = 1L,
            )
            clientQueries.reviveClientContact(
                updatedAt = now,
                clientId = link.clientId.value,
                contactId = link.contactId.value,
                role = link.role.name,
            )
        }
    }

    /** Whether this person's details differ from what is stored, or are not stored at all. */
    private suspend fun YellowTrackDatabase.differsFromStored(contact: Contact): Boolean {
        val stored = contactQueries.selectById(contact.id.value).awaitAsOneOrNull()
        return stored == null || stored.version != contact.audit.version.toLong() || stored.deleted_at != null
    }

    /**
     * Writes a contact, unconditionally.
     *
     * The "only when it differs" test moved to [differsFromStored] so that the decision is
     * made while planning, before the server is told. Keeping a second copy of it here would
     * let this quietly decline to write something the server has already been sent.
     */
    private suspend fun YellowTrackDatabase.writeContact(
        contact: Contact,
        now: Long,
    ) {
        contactQueries.insertOrIgnore(
            id = contact.id.value,
            studio_id = contact.studioId.value,
            first_name = contact.firstName,
            last_name = contact.lastName,
            company = contact.company,
            job_title = contact.jobTitle,
            emails = encodeContactMethods(contact.emails),
            phones = encodeContactMethods(contact.phones),
            notes = contact.notes,
            created_at = contact.audit.createdAt.toEpochMillis(),
            updated_at = now,
            deleted_at = null,
            version = contact.audit.version.toLong(),
        )

        contactQueries.update(
            firstName = contact.firstName,
            lastName = contact.lastName,
            company = contact.company,
            jobTitle = contact.jobTitle,
            emails = encodeContactMethods(contact.emails),
            phones = encodeContactMethods(contact.phones),
            notes = contact.notes,
            updatedAt = now,
            deletedAt = null,
            version = contact.audit.version.toLong(),
            id = contact.id.value,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun YellowTrackDatabase.saveClientContact(
        clientId: ClientId,
        link: ClientContact,
        now: Long,
    ) {
        val contact = link.contact

        contactQueries.insertOrIgnore(
            id = contact.id.value,
            studio_id = contact.studioId.value,
            first_name = contact.firstName,
            last_name = contact.lastName,
            company = contact.company,
            job_title = contact.jobTitle,
            emails = encodeContactMethods(contact.emails),
            phones = encodeContactMethods(contact.phones),
            notes = contact.notes,
            created_at = contact.audit.createdAt.toEpochMillis(),
            updated_at = now,
            deleted_at = null,
            version = contact.audit.version.toLong(),
        )

        contactQueries.update(
            firstName = contact.firstName,
            lastName = contact.lastName,
            company = contact.company,
            jobTitle = contact.jobTitle,
            emails = encodeContactMethods(contact.emails),
            phones = encodeContactMethods(contact.phones),
            notes = contact.notes,
            updatedAt = now,
            deletedAt = null,
            version = contact.audit.version.toLong(),
            id = contact.id.value,
        )

        clientQueries.insertOrIgnoreClientContact(
            id = uuidV7().toString(),
            studio_id = contact.studioId.value,
            client_id = clientId.value,
            contact_id = contact.id.value,
            role = link.role.name,
            created_at = now,
            updated_at = now,
            deleted_at = null,
            version = 1L,
        )

        // The insert above is ignored when the link already exists, so revive it explicitly.
        clientQueries.reviveClientContact(
            updatedAt = now,
            clientId = clientId.value,
            contactId = contact.id.value,
            role = link.role.name,
        )
    }

    /** Joins contact links onto client rows, re-reading whenever either table changes. */
    private fun Flow<List<ClientRow>>.withContacts(db: YellowTrackDatabase): Flow<List<Client>> =
        combine(db.clientQueries.selectContactsForStudio(studioId).asListFlow(dispatcher)) { rows, links ->
            val linksByClient = links.groupBy { it.client_id }

            rows.map { row ->
                row.toDomain(contacts = linksByClient[row.id].orEmpty().map { it.toDomain() })
            }
        }
}

/** LIKE treats `%` and `_` as wildcards; someone searching for "50_50" means the literal. */
private fun String.escapeLikeWildcards(): String = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
