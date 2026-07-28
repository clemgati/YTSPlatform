package com.yellowtrack.platform.feature.clients.domain

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientId

internal interface ClientRepository {
    suspend fun getClients(): List<Client>

    suspend fun getClient(clientId: ClientId): Client?
}
