package com.yellowtrack.platform.feature.clients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.feature.clients.presentation.project.ProjectDetailsScreen
import com.yellowtrack.platform.feature.clients.presentation.project.ProjectDetailsViewModel
import com.yellowtrack.platform.feature.clients.presentation.project.model.PaperworkItem
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ProjectDetailsRoute(
    projectId: ProjectId,
    onBack: () -> Unit,
    onSessionSelected: (SessionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the booking so navigating between two builds a new ViewModel rather than
    // reusing one still bound to the previous identifier.
    val viewModel: ProjectDetailsViewModel =
        koinViewModel(key = projectId.value) { parametersOf(projectId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The booking is gone, so this screen has nothing left to be about. Leaving is part of
    // the removal rather than a courtesy.
    LaunchedEffect(uiState.removed) {
        if (uiState.removed) onBack()
    }

    ProjectDetailsScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onBack = onBack,
        onSessionSelected = onSessionSelected,
        onAddTask = viewModel::addTask,
        onCompleteTask = viewModel::completeTask,
        onReopenTask = viewModel::reopenTask,
        onDeleteTask = viewModel::deleteTask,
        onUpdateProject = viewModel::updateProject,
        onAddDeliverable = viewModel::addDeliverable,
        onSetDeliverableStatus = viewModel::setDeliverableStatus,
        onAddRevision = viewModel::addRevisionRound,
        onRemoveDeliverable = viewModel::deleteDeliverable,
        onRemoveProject = viewModel::deleteProject,
        onRemovePaperwork = { item ->
            when (item.kind) {
                PaperworkItem.Kind.Quote -> viewModel.deleteQuote(item.id)
                PaperworkItem.Kind.Contract -> viewModel.deleteContract(item.id)
            }
        },
        modifier = modifier,
    )
}
