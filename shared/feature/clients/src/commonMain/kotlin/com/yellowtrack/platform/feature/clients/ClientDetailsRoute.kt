package com.yellowtrack.platform.feature.clients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.feature.clients.data.InMemoryClientRepository
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsScreen
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsViewModel

@Composable
fun ClientDetailsRoute(
    clientId: ClientId,
    onBack: () -> Unit,
    onScheduleSession: (ClientId) -> Unit,
    onEditClient: (ClientId) -> Unit,
    onArchiveClient: (ClientId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel =
        remember(clientId) {
            ClientDetailsViewModel(
                clientId = clientId,
                clientRepository = InMemoryClientRepository(),
            )
        }

    val uiState by viewModel.uiState.collectAsState()

    ClientDetailsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onBack = onBack,
        onScheduleSession = {
            onScheduleSession(clientId)
        },
        onEditClient = {
            onEditClient(clientId)
        },
        onArchiveClient = {
            onArchiveClient(clientId)
        },
        modifier = modifier,
    )
}
