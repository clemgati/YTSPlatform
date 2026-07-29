package com.yellowtrack.platform.feature.sessions.presentation

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
import com.yellowtrack.platform.feature.sessions.presentation.mapper.buildSessionGroups
import com.yellowtrack.platform.feature.sessions.presentation.model.BookingOption
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import com.yellowtrack.platform.feature.sessions.presentation.model.coordinates
import com.yellowtrack.platform.feature.sessions.presentation.model.timing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal class SessionsViewModel(
    private val sessionRepository: SessionRepository,
    private val projectRepository: ProjectRepository,
    private val clientRepository: ClientRepository,
    private val studioContext: StudioContext,
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

                    val clientsById = clients.associateBy { it.id }

                    SessionsUiState(
                        groups = if (groups.isEmpty()) UiState.Empty else UiState.Success(groups),
                        totalCount = sessions.size,
                        bookings =
                            projects.map { project ->
                                BookingOption(
                                    id = project.id,
                                    label =
                                        clientsById[project.clientId]
                                            ?.let { "${project.name} — ${it.displayName}" }
                                            ?: project.name,
                                )
                            },
                        today = clock.now().toLocalDateTime(deviceZone).date,
                        zone = deviceZone,
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

    /**
     * Puts a day of work on the calendar.
     *
     * The zone is stored with the session rather than assumed on read: a destination
     * wedding booked from home and a shoot that straddles a daylight-saving boundary both
     * come out wrong when local time is treated as unambiguous.
     */
    fun addSession(session: NewSession) {
        viewModelScope.launch {
            if (session.title.isBlank()) return@launch
            val timing = session.timing(deviceZone) ?: return@launch
            val now = clock.now()

            sessionRepository.saveSession(
                Session(
                    id = SessionId.new(),
                    studioId = studioContext.studioId,
                    projectId = session.projectId,
                    title = session.title.trim(),
                    kind = session.kind,
                    status = session.status,
                    startsAt = timing.startsAt,
                    endsAt = timing.endsAt,
                    timeZoneId = deviceZone.id,
                    locationName = session.locationName.trim().ifBlank { null },
                    locationAddress = session.locationAddress.trim().ifBlank { null },
                    coordinates = session.coordinates(),
                    callTime = timing.callTime,
                    notes = session.notes.trim().ifBlank { null },
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Corrects a session in place.
     *
     * Times resolve against the session's own zone rather than the device's, so a
     * destination wedding edited from home stays at the hour it was booked for instead of
     * sliding by the offset between the two.
     */
    fun updateSession(
        sessionId: SessionId,
        edited: NewSession,
    ) {
        viewModelScope.launch {
            val existing = sessionRepository.getSession(sessionId) ?: return@launch
            val zone = TimeZone.of(existing.timeZoneId)
            if (edited.title.isBlank()) return@launch
            val timing = edited.timing(zone) ?: return@launch
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
     * Moves a shoot to a new date, keeping the original block.
     *
     * This is deliberately not an edit. `SessionStatus.Postponed` exists precisely so the
     * day that was held can still be seen after it moves — a client who moved a date twice
     * in a fortnight is a fact about that booking, and a studio charging a reschedule fee
     * needs the record of what was moved. Overwriting the block would erase both.
     *
     * The new day is scheduled in the same zone as the one it replaces.
     */
    fun moveSession(
        sessionId: SessionId,
        rescheduled: NewSession,
    ) {
        viewModelScope.launch {
            val original = sessionRepository.getSession(sessionId) ?: return@launch
            val zone = TimeZone.of(original.timeZoneId)
            if (rescheduled.title.isBlank()) return@launch
            val timing = rescheduled.timing(zone) ?: return@launch
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
                    // A day that has just been moved is on the calendar again, not
                    // carried over as postponed from the block it replaces.
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
