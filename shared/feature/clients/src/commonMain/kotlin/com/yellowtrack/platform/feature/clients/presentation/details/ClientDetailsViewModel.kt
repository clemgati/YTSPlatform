package com.yellowtrack.platform.feature.clients.presentation.details

import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.domain.ClientRepository
import com.yellowtrack.platform.feature.clients.presentation.details.mapper.toClientDetailsModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class ClientDetailsViewModel(
    private val clientId: ClientId,
    private val clientRepository: ClientRepository,
) {
    private val scope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.Main,
        )

    private val _uiState =
        MutableStateFlow(
            ClientDetailsUiState(
                client = UiState.Loading,
            ),
        )

    val uiState: StateFlow<ClientDetailsUiState> =
        _uiState.asStateFlow()

    init {
        loadClient()
    }

    fun retry() {
        loadClient()
    }

    private fun loadClient() {
        _uiState.update {
            it.copy(
                client = UiState.Loading,
            )
        }

        scope.launch {
            runCatching {
                clientRepository.getClient(clientId)
            }.onSuccess { client ->
                _uiState.update {
                    it.copy(
                        client =
                            if (client == null) {
                                UiState.Error(
                                    message = "Client could not be found.",
                                )
                            } else {
                                UiState.Success(
                                    client.toClientDetailsModel(),
                                )
                            },
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        client =
                            UiState.Error(
                                message =
                                    throwable.message
                                        ?: "Unable to load client details.",
                            ),
                    )
                }
            }
        }
    }
}
