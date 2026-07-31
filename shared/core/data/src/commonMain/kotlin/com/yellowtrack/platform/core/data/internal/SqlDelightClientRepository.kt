package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.database.YellowTrackDatabase
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientId
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

            // Links are replaced wholesale: retiring them all and reviving the ones still
            // present is simpler than diffing, and leaves a tombstone for any genuinely
            // removed — which a synchronising peer will need.
            db.clientQueries.softDeleteContactsForClient(deletedAt = now, clientId = client.id.value)

            client.contacts.forEach { link -> db.saveClientContact(client.id, link, now) }

            db.enqueueForSync(client.studioId.value, SyncTables.CLIENT, client.id.value, OutboxOperation.Upsert, now)
        }
    }

    override suspend fun deleteClient(clientId: ClientId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.clientQueries.softDeleteContactsForClient(deletedAt = now, clientId = clientId.value)
            db.clientQueries.softDelete(deletedAt = now, id = clientId.value)

            db.enqueueForSync(studioId, SyncTables.CLIENT, clientId.value, OutboxOperation.Delete, now)
        }
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
