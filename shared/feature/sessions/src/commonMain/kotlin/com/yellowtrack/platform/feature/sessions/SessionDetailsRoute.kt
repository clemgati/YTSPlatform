package com.yellowtrack.platform.feature.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsScreen
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SessionDetailsRoute(
    sessionId: SessionId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the session so navigating between two builds a new ViewModel rather than
    // reusing one still bound to the previous identifier.
    val viewModel: SessionDetailsViewModel =
        koinViewModel(key = sessionId.value) { parametersOf(sessionId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SessionDetailsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onBack = onBack,
        onUpdateSession = viewModel::updateSession,
        onMoveSession = viewModel::moveSession,
        onAddShot = viewModel::addShot,
        onAddCrew = viewModel::addCrewMember,
        onRemoveCrew = viewModel::deleteCrewMember,
        onAddRelease = viewModel::addRelease,
        onSetReleaseStatus = viewModel::setReleaseStatus,
        onRemoveRelease = viewModel::deleteRelease,
        onToggleShot = viewModel::setShotCaptured,
        onDeleteShot = viewModel::deleteShot,
        modifier = modifier,
    )
}
