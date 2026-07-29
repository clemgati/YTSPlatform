package com.yellowtrack.platform.feature.sessions.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.details.mapper.toDetailsModel
import com.yellowtrack.platform.feature.sessions.presentation.model.BookingOption
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import com.yellowtrack.platform.feature.sessions.presentation.model.coordinates
import com.yellowtrack.platform.feature.sessions.presentation.model.timing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * One shoot day, with the light worked out for where it is.
 *
 * Editing lives here rather than on the list because this is where there is room to show
 * what an edit affects: change the date and the golden hours move with it.
 */
internal class SessionDetailsViewModel(
    private val sessionId: SessionId,
    private val sessionRepository: SessionRepository,
    projectRepository: ProjectRepository,
    clientRepository: ClientRepository,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val deviceZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<SessionDetailsUiState> =
        combine(
            sessionRepository.observeSession(sessionId),
            projectRepository.observeProjects(),
            clientRepository.observeClients(),
            retryTrigger,
        ) { session, projects, clients, _ ->
            if (session == null) {
                SessionDetailsUiState(session = UiState.Error("This session could not be found."))
            } else {
                val project = projects.firstOrNull { it.id == session.projectId }
                val client = project?.let { booking -> clients.firstOrNull { it.id == booking.clientId } }

                SessionDetailsUiState(
                    session = UiState.Success(session.toDetailsModel(project, client, deviceZone)),
                    bookings =
                        projects.map { booking ->
                            BookingOption(
                                id = booking.id,
                                label =
                                    clients
                                        .firstOrNull { it.id == booking.clientId }
                                        ?.let { "${booking.name} — ${it.displayName}" }
                                        ?: booking.name,
                            )
                        },
                    today = clock.now().toLocalDateTime(deviceZone).date,
                )
            }
        }.catch { throwable ->
            emit(
                SessionDetailsUiState(
                    session = UiState.Error(throwable.message ?: "Unable to load this session."),
                ),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SessionDetailsUiState(session = UiState.Loading),
        )

    /**
     * Corrects the session in place.
     *
     * Times resolve against the session's own zone rather than the device's, so a
     * destination wedding edited from home stays at the hour it was booked for.
     */
    fun updateSession(edited: NewSession) {
        viewModelScope.launch {
            val existing = sessionRepository.getSession(sessionId) ?: return@launch
            if (edited.title.isBlank()) return@launch
            val timing = edited.timing(TimeZone.of(existing.timeZoneId)) ?: return@launch
            val now = clock.now()

            sessionRepository.saveSession(
                existing.copy(
                    projectId = edited.projectId,
                    title = edited.title.trim(),
                    kind = edited.kind,
                    status = edited.status,
                    startsAt = timing.startsAt,
                    endsAt = timing.endsAt,
                    locationName = edited.locationName.trim().ifBlank { null },
                    locationAddress = edited.locationAddress.trim().ifBlank { null },
                    coordinates = edited.coordinates(),
                    callTime = timing.callTime,
                    notes = edited.notes.trim().ifBlank { null },
                    audit = existing.audit.touched(now),
                ),
            )
        }
    }

    /**
     * Moves the shoot to a new date, keeping the original block.
     *
     * `SessionStatus.Postponed` exists so the day that was held can still be seen after it
     * moves: a client who moved a date twice in a fortnight is a fact about that booking,
     * and a studio charging a reschedule fee needs the record of what was moved.
     */
    fun moveSession(rescheduled: NewSession) {
        viewModelScope.launch {
            val original = sessionRepository.getSession(sessionId) ?: return@launch
            if (rescheduled.title.isBlank()) return@launch
            val timing = rescheduled.timing(TimeZone.of(original.timeZoneId)) ?: return@launch
            val now = clock.now()

            sessionRepository.saveSession(
                original.copy(
                    status = SessionStatus.Postponed,
                    audit = original.audit.touched(now),
                ),
            )

            sessionRepository.saveSession(
                Session(
                    id = SessionId.new(),
                    studioId = studioContext.studioId,
                    projectId = rescheduled.projectId,
                    title = rescheduled.title.trim(),
                    kind = rescheduled.kind,
                    // A day just moved is on the calendar again, not carried over as
                    // postponed from the block it replaces.
                    status =
                        rescheduled.status.takeIf { it != SessionStatus.Postponed }
                            ?: SessionStatus.Scheduled,
                    startsAt = timing.startsAt,
                    endsAt = timing.endsAt,
                    timeZoneId = original.timeZoneId,
                    locationName = rescheduled.locationName.trim().ifBlank { null },
                    locationAddress = rescheduled.locationAddress.trim().ifBlank { null },
                    coordinates = rescheduled.coordinates(),
                    callTime = timing.callTime,
                    notes = rescheduled.notes.trim().ifBlank { null },
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
