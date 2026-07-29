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
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contact.ContactMethod
import com.yellowtrack.platform.core.model.contact.ContactMethodLabel
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.mapper.toClientDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
import com.yellowtrack.platform.feature.clients.presentation.model.NewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class ClientDetailsViewModel(
    private val clientId: ClientId,
    private val clientRepository: ClientRepository,
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

    /**
     * Corrects the account and the person the form shows.
     *
     * Everything the form does not show is carried across untouched. That matters more
     * than it looks: an account may hold a partner, a planner, and an accounts-payable
     * contact, and this form shows only the primary one. Rebuilding the contact list from
     * what is on screen would silently delete the other three.
     */
    fun updateClient(edited: NewClient) {
        viewModelScope.launch {
            if (!edited.hasName) return@launch

            val existing = clientRepository.getClient(clientId) ?: return@launch
            val now = clock.now()

            // Mirrors Client.primaryContact, so the row the form was filled from is the
            // row it writes back to.
            val primary =
                existing.contacts.firstOrNull { it.role == ClientContactRole.Primary }
                    ?: existing.contacts.firstOrNull()

            val contacts =
                when {
                    primary != null -> {
                        val updated =
                            primary.contact.copy(
                                firstName = edited.contactFirstName.trim(),
                                lastName = edited.contactLastName.trim(),
                                company = edited.company.trim().ifBlank { null },
                                emails = primary.contact.emails.withPrimary(edited.email.trim()),
                                phones = primary.contact.phones.withPrimary(edited.phone.trim()),
                                audit = primary.contact.audit.touched(now),
                            )

                        existing.contacts.map { if (it == primary) it.copy(contact = updated) else it }
                    }

                    edited.hasContact ->
                        existing.contacts +
                            ClientContact(
                                contact =
                                    Contact(
                                        id = ContactId.new(),
                                        studioId = studioContext.studioId,
                                        firstName = edited.contactFirstName.trim(),
                                        lastName = edited.contactLastName.trim(),
                                        company = edited.company.trim().ifBlank { null },
                                        emails = emptyList<ContactMethod>().withPrimary(edited.email.trim()),
                                        phones = emptyList<ContactMethod>().withPrimary(edited.phone.trim()),
                                        audit = AuditMetadata.createdAt(now),
                                    ),
                                role = ClientContactRole.Primary,
                            )

                    else -> existing.contacts
                }

            clientRepository.saveClient(
                existing.copy(
                    accountName = edited.accountName.trim(),
                    accountType = edited.accountType,
                    contacts = contacts,
                    notes = edited.notes.trim().ifBlank { null },
                    audit = existing.audit.touched(now),
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

/**
 * Replaces the primary entry, keeping every other way of reaching the person.
 *
 * A contact may carry a work address and a personal one; the form shows a single field. A
 * blank value removes the primary entry rather than the list, so clearing one email does
 * not discard the others.
 */
private fun List<ContactMethod>.withPrimary(value: String): List<ContactMethod> {
    val current = firstOrNull { it.label == ContactMethodLabel.Primary } ?: firstOrNull()

    return when {
        value.isBlank() -> filterNot { it == current }
        current == null -> this + ContactMethod(value)
        else -> map { if (it == current) it.copy(value = value) else it }
    }
}
