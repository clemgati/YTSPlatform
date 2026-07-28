package com.yellowtrack.platform.feature.sessions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.mapper.buildSessionGroups
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone

internal class SessionsViewModel(
    private val sessionRepository: SessionRepository,
    private val projectRepository: ProjectRepository,
    private val clientRepository: ClientRepository,
    private val clock: AppClock,
    private val deviceZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SessionsUiState> =
        retryTrigger
            .flatMapLatest {
                combine(
                    sessionRepository.observeSessions(),
                    projectRepository.observeProjects(),
                    clientRepository.observeClients(),
                ) { sessions, projects, clients ->
                    val groups = buildSessionGroups(sessions, projects, clients, clock.now(), deviceZone)

                    SessionsUiState(
                        groups = if (groups.isEmpty()) UiState.Empty else UiState.Success(groups),
                        totalCount = sessions.size,
                    )
                }.catch { throwable ->
                    emit(
                        SessionsUiState(
                            groups = UiState.Error(throwable.message ?: "Unable to load sessions."),
                        ),
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = SessionsUiState(groups = UiState.Loading),
            )

    fun retry() {
        retryTrigger.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
