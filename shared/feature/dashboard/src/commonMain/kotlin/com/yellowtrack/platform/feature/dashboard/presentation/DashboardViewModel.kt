package com.yellowtrack.platform.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.GearRepository
import com.yellowtrack.platform.core.data.LeadRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StorageVolumeRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.data.SyncConflictRepository
import com.yellowtrack.platform.core.data.currency
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadConversion
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.lead.LeadStatus
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.dashboard.presentation.mapper.buildStudioStatus
import com.yellowtrack.platform.feature.dashboard.presentation.mapper.toDashboardSummary
import com.yellowtrack.platform.feature.dashboard.presentation.model.NewEnquiry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Was previously a plain class holding one immutable field of sample data.
 *
 * It now observes the repositories, so a session booked elsewhere in the app appears here
 * without a manual refresh.
 */
internal class DashboardViewModel(
    private val clientRepository: ClientRepository,
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository,
    private val leadRepository: LeadRepository,
    private val studioContext: StudioContext,
    private val studioProfileRepository: StudioProfileRepository,
    private val conflictRepository: SyncConflictRepository,
    private val gearRepository: GearRepository,
    private val volumeRepository: StorageVolumeRepository,
    private val clock: AppClock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

    /**
     * The two lead lists, grouped so the join stays within `combine`'s typed arity.
     *
     * They answer different questions: what needs a reply today, and what has ever come in.
     */
    private data class Enquiries(
        val awaitingReply: List<Lead>,
        val all: List<Lead>,
    )

    /** The studio's own things, which is what readiness is read from. */
    private data class Kit(
        val gear: List<GearItem>,
        val volumes: List<StorageVolume>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> =
        retryTrigger
            .flatMapLatest {
                // "Today" is resolved when the dashboard is observed. A device left running
                // across midnight will not roll over until the screen is revisited.
                val today = clock.now().toLocalDateTime(timeZone).date
                val startOfToday = today.atStartOfDayIn(timeZone)
                val startOfTomorrow = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)

                combine(
                    sessionRepository.observeSessionsBetween(startOfToday, startOfTomorrow),
                    projectRepository.observeProjects(),
                    clientRepository.observeClients(),
                    combine(
                        leadRepository.observeAwaitingResponse(),
                        leadRepository.observeLeads(),
                        ::Enquiries,
                    ),
                    combine(
                        gearRepository.observeGear(),
                        volumeRepository.observeVolumes(),
                        conflictRepository.observeUnresolvedCount(),
                    ) { gear, volumes, conflicts -> Kit(gear, volumes) to conflicts },
                ) { sessions, projects, clients, enquiries, (kit, unresolvedConflicts) ->
                    DashboardUiState(
                        summary =
                            UiState.Success(
                                toDashboardSummary(
                                    todaysSessions = sessions,
                                    projects = projects,
                                    clients = clients,
                                    enquiriesAwaitingReply = enquiries.awaitingReply,
                                    allEnquiries = enquiries.all,
                                    now = clock.now(),
                                    studioStatus = buildStudioStatus(kit.gear, kit.volumes),
                                ).copy(unresolvedConflicts = unresolvedConflicts),
                            ),
                    )
                }.catch { throwable ->
                    emit(
                        DashboardUiState(
                            summary = UiState.Error(throwable.message ?: "Unable to load the dashboard."),
                        ),
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DashboardUiState(summary = UiState.Loading),
            )

    fun retry() {
        retryTrigger.value += 1
    }

    /**
     * Turns a won enquiry into a client, without retyping what it already says.
     *
     * The client is saved first. If that fails the enquiry is untouched and the studio can
     * try again; the other order would leave an enquiry claiming a client that does not
     * exist, which is a broken link nothing would ever notice.
     *
     * Refuses one that has already produced a client, because converting twice makes a second
     * client for the same person — and a screen can be pressed twice.
     */
    fun convertEnquiryToClient(
        leadId: LeadId,
        openBooking: Boolean,
    ) {
        viewModelScope.launch {
            val lead = leadRepository.getLead(leadId) ?: return@launch
            if (lead.convertedClientId != null) return@launch

            val now = clock.now()
            val clientId = ClientId.new()

            clientRepository.saveClient(LeadConversion.clientFrom(lead, clientId, ContactId.new(), now))

            // The booking needs the client to exist, and the enquiry is written last so it
            // never points at either before they are there.
            val projectId =
                ProjectId.new().takeIf { openBooking }?.also { id ->
                    projectRepository.saveProject(LeadConversion.projectFrom(lead, id, clientId, now))
                }

            leadRepository.saveLead(LeadConversion.converted(lead, clientId, now, projectId))
        }
    }

    /**
     * Stamps the moment the studio first replied.
     *
     * Only ever set once. A later reply does not overwrite the first, because the figure
     * that predicts bookings is time-to-first-response, not time-to-most-recent.
     */
    fun markEnquiryReplied(leadId: LeadId) {
        viewModelScope.launch {
            val lead = leadRepository.getLead(leadId) ?: return@launch
            if (lead.firstResponseAt != null) return@launch

            val now = clock.now()

            leadRepository.saveLead(
                lead.copy(
                    firstResponseAt = now,
                    status = if (lead.status == LeadStatus.New) LeadStatus.Contacted else lead.status,
                    audit = lead.audit.touched(now),
                ),
            )
        }
    }

    /**
     * Removes an enquiry.
     *
     * A lead is a leaf: nothing in the application points at one, so there is nothing here
     * to hold it and no cascade to refuse. Winning an enquiry creates a client and a
     * booking, and neither carries the lead's identifier — the conversion copies what it
     * needs rather than pointing back.
     *
     * Which makes this the plainest case in the sweep and the last of it. Spam arrives by
     * the same form as real work, and a studio that logged one had no way to be rid of it.
     */
    fun deleteEnquiry(leadId: LeadId) {
        viewModelScope.launch { leadRepository.deleteLead(leadId) }
    }

    /**
     * Logs an enquiry, or corrects one already logged.
     *
     * Enquiries are typed in a hurry, usually while reading the message they came from, so
     * a mistyped name or a missing phone number is the ordinary case rather than the odd
     * one. Everything the form does not show is carried across: the moment it arrived, the
     * moment it was first replied to, and how far it has got — none of which is the form's
     * business and all of which the response-time figure is measured from.
     */
    fun saveEnquiry(
        enquiry: NewEnquiry,
        existingId: LeadId? = null,
    ) {
        viewModelScope.launch {
            if (enquiry.name.isBlank()) return@launch

            val now = clock.now()
            val studioCurrency = studioProfileRepository.currency()
            val existing = existingId?.let { leadRepository.getLead(it) }

            leadRepository.saveLead(
                Lead(
                    id = existing?.id ?: LeadId.new(),
                    studioId = studioContext.studioId,
                    name = enquiry.name,
                    source = enquiry.source,
                    status = existing?.status ?: LeadStatus.New,
                    receivedAt = existing?.receivedAt ?: now,
                    firstResponseAt = existing?.firstResponseAt,
                    email = enquiry.email,
                    phone = enquiry.phone,
                    serviceLine = enquiry.serviceLine,
                    budgetLow = enquiry.budgetLow?.let { parseMoney(it, studioCurrency) },
                    budgetHigh = enquiry.budgetHigh?.let { parseMoney(it, studioCurrency) },
                    referredBy = enquiry.referredBy,
                    audit = existing?.audit?.touched(now) ?: AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
