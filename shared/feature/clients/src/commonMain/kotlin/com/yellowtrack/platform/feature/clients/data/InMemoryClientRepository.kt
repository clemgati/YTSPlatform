package com.yellowtrack.platform.feature.clients.data

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.feature.clients.domain.ClientRepository

internal class InMemoryClientRepository : ClientRepository {
    private val clients =
        listOf(
            Client(
                id = ClientId("client-1"),
                firstName = "John",
                lastName = "Smith",
            ),
            Client(
                id = ClientId("client-2"),
                firstName = "Sarah",
                lastName = "Johnson",
            ),
            Client(
                id = ClientId("client-3"),
                firstName = "Emily",
                lastName = "Davis",
            ),
            Client(
                id = ClientId("client-4"),
                firstName = "Michael",
                lastName = "Brown",
            ),
        )

    override suspend fun getClients(): List<Client> = clients

    override suspend fun getClient(clientId: ClientId): Client? =
        clients.firstOrNull { client ->
            client.id == clientId
        }
}
