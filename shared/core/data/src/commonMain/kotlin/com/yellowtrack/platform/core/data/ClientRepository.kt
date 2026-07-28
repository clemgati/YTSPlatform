package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientId
import kotlinx.coroutines.flow.Flow

/**
 * Client accounts.
 *
 * Lives in `core:data` rather than inside the clients feature because more than one
 * feature needs it — Dashboard and Sessions both do — and features must not depend on
 * one another.
 */
interface ClientRepository {
    fun observeClients(): Flow<List<Client>>

    fun observeClient(clientId: ClientId): Flow<Client?>

    /** Matches on account name and on any attached contact's name or company. */
    fun searchClients(query: String): Flow<List<Client>>

    suspend fun getClient(clientId: ClientId): Client?

    /** Inserts or updates the account and replaces its contact links in one transaction. */
    suspend fun saveClient(client: Client)

    /** Soft-deletes the account and its contact links, leaving a synchronisable tombstone. */
    suspend fun deleteClient(clientId: ClientId)
}
