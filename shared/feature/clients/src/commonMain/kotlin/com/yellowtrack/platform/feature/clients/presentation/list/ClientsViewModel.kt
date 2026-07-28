package com.yellowtrack.platform.feature.clients.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.list.mapper.toClientSummaries
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Extends [ViewModel] so that [viewModelScope] is cancelled with the screen. The previous
 * implementation created its own `CoroutineScope` that was never cancelled, which leaked
 * a coroutine on every disposal.
 */
internal class ClientsViewModel(
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val clock: AppClock,
) : ViewModel() {
    private val query = MutableStateFlow("")

    /**
     * Incremented by [retry]. A separate signal is needed because `StateFlow` conflates
     * equal values, so re-assigning the same query would emit nothing.
     */
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ClientsUiState> =
        combine(query, retryTrigger) { currentQuery, _ -> currentQuery }
            .flatMapLatest { currentQuery ->
                combine(
                    clientRepository.searchClients(currentQuery),
                    projectRepository.observeProjects(),
                    sessionRepository.observeSessions(),
                ) { clients, projects, sessions ->
                    val summaries = clients.toClientSummaries(projects, sessions, clock.now())

                    ClientsUiState(
                        clients = if (summaries.isEmpty()) UiState.Empty else UiState.Success(summaries),
                        query = currentQuery,
                        isSearching = currentQuery.isNotBlank(),
                    )
                }.catch { throwable ->
                    emit(
                        ClientsUiState(
                            clients = UiState.Error(throwable.message ?: "Unable to load clients."),
                            query = currentQuery,
                            isSearching = currentQuery.isNotBlank(),
                        ),
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ClientsUiState(clients = UiState.Loading),
            )

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    /** Re-subscribes to the repository after an error. */
    fun retry() {
        retryTrigger.value += 1
    }

    private companion object {
        /** Survives a configuration change without tearing down and rebuilding the query. */
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
