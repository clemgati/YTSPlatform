package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory client repository for feature tests.
 *
 * [failure] lets a test drive the error path, which is otherwise hard to reach and
 * therefore usually untested.
 */
class FakeClientRepository(
    initial: List<Client> = emptyList(),
) : ClientRepository {
    private val state = MutableStateFlow(initial)

    var failure: Throwable? = null

    override fun observeClients(): Flow<List<Client>> = state.map { clients -> failure?.let { throw it } ?: clients }

    override fun observeClient(clientId: ClientId): Flow<Client?> =
        state.map { clients -> failure?.let { throw it } ?: clients.firstOrNull { it.id == clientId } }

    override fun searchClients(query: String): Flow<List<Client>> =
        state.map { clients ->
            failure?.let { throw it }

            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                clients
            } else {
                clients.filter { client ->
                    client.accountName.contains(trimmed, ignoreCase = true) ||
                        client.contacts.any { it.contact.displayName.contains(trimmed, ignoreCase = true) }
                }
            }
        }

    override suspend fun getClient(clientId: ClientId): Client? = state.value.firstOrNull { it.id == clientId }

    override suspend fun saveClient(client: Client) {
        state.value = state.value.filterNot { it.id == client.id } + client
    }

    override suspend fun deleteClient(clientId: ClientId) {
        state.value = state.value.filterNot { it.id == clientId }
    }
}
