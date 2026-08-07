package com.yellowtrack.platform.feature.sessions.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.sync.WriteFailures
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
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
    /** Why the last write did not happen. ADR 0012 made these able to fail. */
    private val writes = WriteFailures()

    val writeFailureMessage: StateFlow<String?> = writes.message

    fun dismissWriteFailure() = writes.dismiss()

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
        writes.launchWrite(viewModelScope) {
            if (session.title.isBlank()) return@launchWrite
            val timing = session.timing(deviceZone) ?: return@launchWrite
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

    fun retry() {
        retryTrigger.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
