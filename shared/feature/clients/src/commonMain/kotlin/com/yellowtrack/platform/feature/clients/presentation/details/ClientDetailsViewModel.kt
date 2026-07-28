package com.yellowtrack.platform.feature.clients.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.mapper.toClientDetailsModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

internal class ClientDetailsViewModel(
    clientId: ClientId,
    clientRepository: ClientRepository,
    projectRepository: ProjectRepository,
    sessionRepository: SessionRepository,
    clock: AppClock,
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<ClientDetailsUiState> =
        combine(
            clientRepository.observeClient(clientId),
            projectRepository.observeProjectsForClient(clientId),
            sessionRepository.observeSessions(),
            retryTrigger,
        ) { client, projects, allSessions, _ ->
            if (client == null) {
                ClientDetailsUiState(client = UiState.Error("Client could not be found."))
            } else {
                // A session belongs to a project and a project belongs to a client, so a
                // client's sessions are reached through their projects.
                val projectIds = projects.map { it.id }.toSet()
                val sessions = allSessions.filter { it.projectId in projectIds }

                ClientDetailsUiState(
                    client = UiState.Success(client.toClientDetailsModel(sessions, clock.now())),
                )
            }
        }.catch { throwable ->
            emit(
                ClientDetailsUiState(
                    client = UiState.Error(throwable.message ?: "Unable to load client details."),
                ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ClientDetailsUiState(client = UiState.Loading),
        )

    fun retry() {
        retryTrigger.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
