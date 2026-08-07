package com.yellowtrack.platform.feature.clients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsScreen
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ClientDetailsRoute(
    clientId: ClientId,
    onBack: () -> Unit,
    onScheduleSession: (ClientId) -> Unit,
    onBookingSelected: (ProjectId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the client so navigating between two clients builds a new ViewModel rather
    // than reusing one still bound to the previous identifier.
    val viewModel: ClientDetailsViewModel =
        koinViewModel(key = clientId.value) { parametersOf(clientId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val writeFailure by viewModel.writeFailureMessage.collectAsStateWithLifecycle()

    // The account this screen is about no longer exists, so there is nothing here to stay
    // for. Leaving is part of the removal rather than a courtesy: a details screen bound to
    // a deleted identifier can only ever show an error about itself.
    LaunchedEffect(uiState.removed) {
        if (uiState.removed) onBack()
    }

    ClientDetailsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onBack = onBack,
        onScheduleSession = { onScheduleSession(clientId) },
        onAddProject = viewModel::addProject,
        onOpenBooking = onBookingSelected,
        onUpdateClient = viewModel::updateClient,
        onRemoveClient = viewModel::deleteClient,
        writeFailure = writeFailure,
        modifier = modifier,
    )
}
