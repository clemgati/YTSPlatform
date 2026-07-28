package com.yellowtrack.platform.feature.clients.presentation.list

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.domain.ClientRepository
import com.yellowtrack.platform.feature.clients.presentation.list.mapper.toClientSummaries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ClientsViewModel(
    private val clientRepository: ClientRepository,
) {
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main,
        )

    private val _uiState =
        MutableStateFlow(
            ClientsUiState(
                clients = UiState.Loading,
            ),
        )

    val uiState: StateFlow<ClientsUiState> =
        _uiState.asStateFlow()

    init {
        loadClients()
    }

    fun retry() {
        loadClients()
    }

    private fun loadClients() {
        _uiState.update {
            it.copy(
                clients = UiState.Loading,
            )
        }

        scope.launch {
            runCatching {
                clientRepository.getClients()
            }.onSuccess { clients ->
                _uiState.update {
                    it.copy(
                        clients =
                            if (clients.isEmpty()) {
                                UiState.Empty
                            } else {
                                UiState.Success(
                                    clients.toClientSummaries(),
                                )
                            },
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        clients =
                            UiState.Error(
                                message =
                                    throwable.message
                                        ?: "Unable to load clients.",
                            ),
                    )
                }
            }
        }
    }
}
