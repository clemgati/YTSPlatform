package com.yellowtrack.platform.feature.clients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsScreen
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ClientsRoute(
    onClientSelected: (ClientId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ClientsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val writeFailure by viewModel.writeFailureMessage.collectAsStateWithLifecycle()

    ClientsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onQueryChange = viewModel::onQueryChange,
        onClientSelected = onClientSelected,
        onAddClient = viewModel::addClient,
        writeFailure = writeFailure,
        modifier = modifier,
    )
}
