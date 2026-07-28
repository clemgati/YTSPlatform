package com.yellowtrack.platform.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yellowtrack.platform.core.model.client.ClientId

class AppState internal constructor() {
    var currentDestination by mutableStateOf(AppDestination.Dashboard)
        private set

    var selectedClientId by mutableStateOf<ClientId?>(null)
        private set

    fun navigateTopLevel(destination: AppDestination) {
        currentDestination = destination
        selectedClientId = null
    }

    fun openClient(clientId: ClientId) {
        currentDestination = AppDestination.Clients
        selectedClientId = clientId
    }

    fun closeClientDetails() {
        selectedClientId = null
    }
}

@Composable
fun rememberAppState(): AppState =
    remember {
        AppState()
    }
