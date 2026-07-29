package com.yellowtrack.platform.feature.clients

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.feature.clients.presentation.project.ProjectDetailsScreen
import com.yellowtrack.platform.feature.clients.presentation.project.ProjectDetailsViewModel
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
        modifier = modifier,
    )
}
