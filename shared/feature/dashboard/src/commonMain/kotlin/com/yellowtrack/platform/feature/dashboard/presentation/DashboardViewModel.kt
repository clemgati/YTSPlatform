package com.yellowtrack.platform.feature.dashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.LeadRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.lead.LeadStatus
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.dashboard.presentation.mapper.toDashboardSummary
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatus
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
    private val clock: AppClock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val currency: CurrencyCode = CurrencyCode.USD,
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

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
                    leadRepository.observeAwaitingResponse(),
                ) { sessions, projects, clients, waitingEnquiries ->
                    DashboardUiState(
                        summary =
                            UiState.Success(
                                toDashboardSummary(
                                    todaysSessions = sessions,
                                    projects = projects,
                                    clients = clients,
                                    enquiriesAwaitingReply = waitingEnquiries,
                                    now = clock.now(),
                                    // Gear and readiness tracking arrive with the Studio
                                    // milestone. Until then the section shows its empty
                                    // state rather than invented checkboxes.
                                    studioStatus = DashboardStudioStatus(items = emptyList()),
                                ),
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

    fun addEnquiry(enquiry: NewEnquiry) {
        viewModelScope.launch {
            val now = clock.now()

            leadRepository.saveLead(
                Lead(
                    id = LeadId.new(),
                    studioId = studioContext.studioId,
                    name = enquiry.name,
                    source = enquiry.source,
                    status = LeadStatus.New,
                    receivedAt = now,
                    email = enquiry.email,
                    phone = enquiry.phone,
                    serviceLine = enquiry.serviceLine,
                    budgetLow = enquiry.budgetLow?.let { parseMoney(it, currency) },
                    budgetHigh = enquiry.budgetHigh?.let { parseMoney(it, currency) },
                    referredBy = enquiry.referredBy,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
