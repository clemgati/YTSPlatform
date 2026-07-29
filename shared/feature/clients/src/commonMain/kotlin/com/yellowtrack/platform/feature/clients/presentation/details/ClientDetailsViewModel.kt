package com.yellowtrack.platform.feature.clients.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.mapper.toClientDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class ClientDetailsViewModel(
    private val clientId: ClientId,
    clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    sessionRepository: SessionRepository,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val currency: CurrencyCode = CurrencyCode.USD,
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

    /**
     * Opens a booking against this client.
     *
     * The status stamp is written with the status rather than after it: a booking recorded
     * as Booked with no `bookedAt` cannot say when the date was taken, which is the figure
     * every later question about that job is measured from.
     */
    fun addProject(project: NewProject) {
        viewModelScope.launch {
            if (project.name.isBlank()) return@launch

            val contractValue =
                when {
                    project.contractValue.isBlank() -> null
                    else -> parseMoney(project.contractValue, currency)?.takeIf { it.isPositive } ?: return@launch
                }

            val now = clock.now()

            projectRepository.saveProject(
                Project(
                    id = ProjectId.new(),
                    studioId = studioContext.studioId,
                    clientId = clientId,
                    name = project.name.trim(),
                    serviceLine = project.serviceLine,
                    status = project.status,
                    contractValue = contractValue,
                    // Every booking was enquired about at some point, even one entered
                    // already booked; the enquiry is what the booking rate is measured
                    // against, so it is never left blank.
                    enquiredAt = now,
                    bookedAt = now.takeIf { project.status.isCommitted },
                    notes = project.notes.trim().ifBlank { null },
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    fun retry() {
        retryTrigger.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
