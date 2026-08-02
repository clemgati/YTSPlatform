package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.database.YellowTrackDatabase
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactLinkId
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.contact.Contact
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import com.yellowtrack.platform.core.database.Client as ClientRow

internal class SqlDelightClientRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    ClientRepository {
    private val studioId get() = studioContext.studioId.value

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

            db.reconcileContacts(client, now)

            db.enqueueForSync(client.studioId.value, SyncTables.CLIENT, client.id.value, OutboxOperation.Upsert, now)
        }
    }

    override suspend fun deleteClient(clientId: ClientId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            // Each link is retired individually and queued, because a peer learns a link is
            // gone from the link's own tombstone. Retiring them in bulk without queueing
            // would delete them here and leave them attached everywhere else.
            db.clientQueries.selectLiveClientContactLinks(clientId.value).awaitAsList().forEach { row ->
                db.clientQueries.softDeleteClientContactById(deletedAt = now, id = row.id)
                db.enqueueForSync(studioId, SyncTables.CLIENT_CONTACT, row.id, OutboxOperation.Delete, now)
            }

            db.clientQueries.softDelete(deletedAt = now, id = clientId.value)

            db.enqueueForSync(studioId, SyncTables.CLIENT, clientId.value, OutboxOperation.Delete, now)
        }
    }

    /**
     * Brings the stored contacts and links into line with what was passed, touching only
     * what actually changed.
     *
     * This used to retire every link and revive the survivors, which was simpler and was
     * fine while links stayed on the device. It is not fine now they synchronise: each save
     * bumped every link's version twice, so saving a client would re-upload contacts nobody
     * had edited, and two devices changing unrelated fields would conflict over links
     * neither had touched. ADR 0008 assumed conflicts would be rare; that assumption has to
     * be earned here.
     *
     * A link is identified for this purpose by the contact and role it joins, because that
     * is what the caller can express — it passes `ClientContact`, which has no link id. The
     * row's own id, once assigned, is never regenerated: it is what a second device knows
     * the row by.
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun YellowTrackDatabase.reconcileContacts(
        client: Client,
        now: Long,
    ) {
        val studio = client.studioId.value
        val existing = clientQueries.selectLiveClientContactLinks(client.id.value).awaitAsList()
        val wantedKeys = client.contacts.map { it.contact.id.value to it.role.name }.toSet()

        existing
            .filter { (it.contact_id to it.role) !in wantedKeys }
            .forEach { row ->
                clientQueries.softDeleteClientContactById(deletedAt = now, id = row.id)
                enqueueForSync(studio, SyncTables.CLIENT_CONTACT, row.id, OutboxOperation.Delete, now)
            }

        val byKey = existing.associateBy { it.contact_id to it.role }

        client.contacts.forEach { link ->
            saveContactIfChanged(link.contact, now)

            if ((link.contact.id.value to link.role.name) in byKey) return@forEach

            // New attachment. The unique index on (client_id, contact_id, role) means an
            // insert racing a revived tombstone is ignored rather than duplicated.
            val id = ClientContactLinkId.new().value

            clientQueries.insertOrIgnoreClientContact(
                id = id,
                studio_id = studio,
                client_id = client.id.value,
                contact_id = link.contact.id.value,
                role = link.role.name,
                created_at = now,
                updated_at = now,
                deleted_at = null,
                version = 1L,
            )
            clientQueries.reviveClientContact(
                updatedAt = now,
                clientId = client.id.value,
                contactId = link.contact.id.value,
                role = link.role.name,
            )

            val stored =
                clientQueries
                    .selectLiveClientContactLinks(client.id.value)
                    .awaitAsList()
                    .firstOrNull { it.contact_id == link.contact.id.value && it.role == link.role.name }

            stored?.let { enqueueForSync(studio, SyncTables.CLIENT_CONTACT, it.id, OutboxOperation.Upsert, now) }
        }
    }

    /**
     * Writes a contact only when it differs, so that attaching an unedited person to a
     * second account does not queue their details for upload again.
     */
    private suspend fun YellowTrackDatabase.saveContactIfChanged(
        contact: Contact,
        now: Long,
    ) {
        val stored = contactQueries.selectById(contact.id.value).awaitAsOneOrNull()

        if (stored != null && stored.version == contact.audit.version.toLong() && stored.deleted_at == null) return

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

        enqueueForSync(contact.studioId.value, SyncTables.CONTACT, contact.id.value, OutboxOperation.Upsert, now)
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
