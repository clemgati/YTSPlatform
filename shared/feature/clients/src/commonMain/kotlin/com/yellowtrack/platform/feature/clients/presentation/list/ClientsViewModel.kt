package com.yellowtrack.platform.feature.clients.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contact.ContactMethod
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.list.mapper.toClientSummaries
import com.yellowtrack.platform.feature.clients.presentation.list.model.NewClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Extends [ViewModel] so that [viewModelScope] is cancelled with the screen. The previous
 * implementation created its own `CoroutineScope` that was never cancelled, which leaked
 * a coroutine on every disposal.
 */
internal class ClientsViewModel(
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val studioContext: StudioContext,
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

    /**
     * Takes on a client account, with its first contact if one was given.
     *
     * A contact is only built when something identifies a person: an account with an empty
     * contact attached looks populated in every list while being no more reachable than
     * one with none, which is worse than plainly having nobody.
     *
     * A blank account name is left blank rather than filled in from the contact, because
     * `Client.displayName` already falls back to the primary contact. Copying the name in
     * would freeze it — renaming the person later would leave the account still addressed
     * by the old name.
     */
    fun addClient(client: NewClient) {
        viewModelScope.launch {
            if (!client.hasName) return@launch

            val now = clock.now()
            val contact =
                if (client.hasContact) {
                    Contact(
                        id = ContactId.new(),
                        studioId = studioContext.studioId,
                        firstName = client.contactFirstName.trim(),
                        lastName = client.contactLastName.trim(),
                        company = client.company.trim().ifBlank { null },
                        emails =
                            client.email
                                .trim()
                                .ifBlank { null }
                                ?.let { listOf(ContactMethod(it)) }
                                .orEmpty(),
                        phones =
                            client.phone
                                .trim()
                                .ifBlank { null }
                                ?.let { listOf(ContactMethod(it)) }
                                .orEmpty(),
                        audit = AuditMetadata.createdAt(now),
                    )
                } else {
                    null
                }

            clientRepository.saveClient(
                Client(
                    id = ClientId.new(),
                    studioId = studioContext.studioId,
                    accountName = client.accountName.trim(),
                    accountType = client.accountType,
                    contacts =
                        contact
                            ?.let { listOf(ClientContact(contact = it, role = ClientContactRole.Primary)) }
                            .orEmpty(),
                    notes = client.notes.trim().ifBlank { null },
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

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
