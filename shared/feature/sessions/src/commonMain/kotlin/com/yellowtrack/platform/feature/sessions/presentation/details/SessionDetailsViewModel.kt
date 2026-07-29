package com.yellowtrack.platform.feature.sessions.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.CrewRepository
import com.yellowtrack.platform.core.data.MediaCopyRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.ShotRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.TalentReleaseRepository
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.release.ReleaseStatus
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.details.mapper.toDetailsModel
import com.yellowtrack.platform.feature.sessions.presentation.model.BookingOption
import com.yellowtrack.platform.feature.sessions.presentation.model.NewCrewMember
import com.yellowtrack.platform.feature.sessions.presentation.model.NewMediaCopy
import com.yellowtrack.platform.feature.sessions.presentation.model.NewRelease
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import com.yellowtrack.platform.feature.sessions.presentation.model.NewShot
import com.yellowtrack.platform.feature.sessions.presentation.model.coordinates
import com.yellowtrack.platform.feature.sessions.presentation.model.timing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
    private val shotRepository: ShotRepository,
    private val crewRepository: CrewRepository,
    private val releaseRepository: TalentReleaseRepository,
    private val mediaCopyRepository: MediaCopyRepository,
    projectRepository: ProjectRepository,
    clientRepository: ClientRepository,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val deviceZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

    /**
     * The day itself, grouped so the join stays within `combine`'s typed arity.
     *
     * Six sources would otherwise need an untyped array and a cast per element; the Ledger
     * groups for the same reason, and the destructuring reads as named things rather than
     * positions.
     */
    private data class Day(
        val session: Session?,
        val shots: List<Shot>,
        val crew: List<CrewMember>,
        val releases: List<TalentRelease>,
        val mediaCopies: List<MediaCopy>,
    )

    private data class Booking(
        val projects: List<Project>,
        val clients: List<Client>,
    )

    val uiState: StateFlow<SessionDetailsUiState> =
        combine(
            combine(
                sessionRepository.observeSession(sessionId),
                shotRepository.observeShotsForSession(sessionId),
                crewRepository.observeCrewForSession(sessionId),
                releaseRepository.observeReleasesForSession(sessionId),
                mediaCopyRepository.observeCopiesForSession(sessionId),
                ::Day,
            ),
            combine(
                projectRepository.observeProjects(),
                clientRepository.observeClients(),
                ::Booking,
            ),
            retryTrigger,
        ) { day, booking, _ ->
            val session = day.session

            if (session == null) {
                SessionDetailsUiState(session = UiState.Error("This session could not be found."))
            } else {
                val project = booking.projects.firstOrNull { it.id == session.projectId }
                val client =
                    project?.let { it -> booking.clients.firstOrNull { candidate -> candidate.id == it.clientId } }

                SessionDetailsUiState(
                    session =
                        UiState.Success(
                            session.toDetailsModel(
                                project,
                                client,
                                day.shots,
                                day.crew,
                                day.releases,
                                day.mediaCopies,
                                deviceZone,
                            ),
                        ),
                    bookings =
                        booking.projects.map { project ->
                            BookingOption(
                                id = project.id,
                                label =
                                    booking.clients
                                        .firstOrNull { it.id == project.clientId }
                                        ?.let { "${project.name} — ${it.displayName}" }
                                        ?: project.name,
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

    /**
     * Adds a shot to the list.
     *
     * Appended to the end of its group rather than the end of the list, so a shot
     * remembered late still lands with the people it needs — writing "and one with
     * Grandma Ruth" at the bottom is how a group gets called back after it was released.
     */
    fun addShot(shot: NewShot) {
        viewModelScope.launch {
            if (shot.description.isBlank()) return@launch

            val group = shot.group.trim().ifBlank { null }
            val existing = shotRepository.observeShotsForSession(sessionId).first()
            val nextPosition =
                existing
                    .filter { it.group?.trim().orEmpty() == group.orEmpty() }
                    .maxOfOrNull { it.position }
                    ?.plus(1)
                    ?: existing.size

            val now = clock.now()

            shotRepository.saveShot(
                Shot(
                    id = ShotId.new(),
                    studioId = studioContext.studioId,
                    sessionId = sessionId,
                    description = shot.description.trim(),
                    group = group,
                    people = shot.people.trim().ifBlank { null },
                    position = nextPosition,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Marks a shot taken, or puts it back on the list.
     *
     * The moment is stamped with the state, so a list ticked off on the day can still say
     * when each group was actually worked.
     */
    fun setShotCaptured(
        shotId: ShotId,
        isCaptured: Boolean,
    ) {
        viewModelScope.launch {
            val shot = shotRepository.getShot(shotId) ?: return@launch
            val now = clock.now()

            shotRepository.saveShot(
                shot.copy(
                    isCaptured = isCaptured,
                    capturedAt = now.takeIf { isCaptured },
                    audit = shot.audit.touched(now),
                ),
            )
        }
    }

    fun deleteShot(shotId: ShotId) {
        viewModelScope.launch { shotRepository.deleteShot(shotId) }
    }

    /** Adds someone to the day, with the time they are due. */
    fun addCrewMember(member: NewCrewMember) {
        viewModelScope.launch {
            if (member.name.isBlank()) return@launch

            val session = sessionRepository.getSession(sessionId) ?: return@launch
            val zone = TimeZone.of(session.timeZoneId)
            val day = session.startsAt.toLocalDateTime(zone).date

            val callTime =
                when {
                    member.callTime.isBlank() -> null
                    else ->
                        runCatching { LocalTime.parse(member.callTime.trim()) }
                            .getOrNull()
                            ?.let { LocalDateTime(day, it).toInstant(zone) }
                            ?: return@launch
                }

            val now = clock.now()

            crewRepository.saveCrewMember(
                CrewMember(
                    id = CrewMemberId.new(),
                    studioId = studioContext.studioId,
                    sessionId = sessionId,
                    name = member.name.trim(),
                    role = member.role,
                    phone = member.phone.trim().ifBlank { null },
                    callTime = callTime,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    fun deleteCrewMember(crewMemberId: CrewMemberId) {
        viewModelScope.launch { crewRepository.deleteCrewMember(crewMemberId) }
    }

    /**
     * Records that someone was photographed, pending their permission.
     *
     * Pending rather than signed: the paper either exists or it does not, and starting at
     * signed would let a studio believe it holds permission it has never collected.
     */
    fun addRelease(release: NewRelease) {
        viewModelScope.launch {
            if (release.personName.isBlank()) return@launch
            val now = clock.now()

            releaseRepository.saveRelease(
                TalentRelease(
                    id = TalentReleaseId.new(),
                    studioId = studioContext.studioId,
                    sessionId = sessionId,
                    personName = release.personName.trim(),
                    kind = release.kind,
                    status = ReleaseStatus.Pending,
                    guardianName = release.guardianName.trim().ifBlank { null },
                    email = release.email.trim().ifBlank { null },
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Moves a release to a new state, stamping the date with the status.
     *
     * The date is written when it is marked signed and cleared when it is not, so a
     * release can never claim permission was given without saying when — the question
     * asked the moment it is challenged.
     */
    fun setReleaseStatus(
        releaseId: TalentReleaseId,
        status: ReleaseStatus,
    ) {
        viewModelScope.launch {
            val release = releaseRepository.getRelease(releaseId) ?: return@launch
            val now = clock.now()

            releaseRepository.saveRelease(
                release.copy(
                    status = status,
                    signedAt = now.takeIf { status == ReleaseStatus.Signed },
                    audit = release.audit.touched(now),
                ),
            )
        }
    }

    fun deleteRelease(releaseId: TalentReleaseId) {
        viewModelScope.launch { releaseRepository.deleteRelease(releaseId) }
    }

    /**
     * Records that the files exist somewhere.
     *
     * Copied now, unverified: the copy has just been made, and whether it can still be
     * read is a separate question asked later. Marking it verified on creation would make
     * every backup look checked when none had been.
     */
    fun addMediaCopy(copy: NewMediaCopy) {
        viewModelScope.launch {
            if (copy.volumeName.isBlank()) return@launch
            val now = clock.now()

            mediaCopyRepository.saveCopy(
                MediaCopy(
                    id = MediaCopyId.new(),
                    studioId = studioContext.studioId,
                    sessionId = sessionId,
                    volumeName = copy.volumeName.trim(),
                    kind = copy.kind,
                    isOffsite = copy.isOffsite,
                    copiedAt = now,
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /**
     * Records that a copy was opened and found readable.
     *
     * A drive can fail silently, so this is the only thing that distinguishes a backup a
     * studio has from one it believes it has.
     */
    fun verifyMediaCopy(copyId: MediaCopyId) {
        viewModelScope.launch {
            val copy = mediaCopyRepository.getCopy(copyId) ?: return@launch
            val now = clock.now()

            mediaCopyRepository.saveCopy(copy.copy(verifiedAt = now, audit = copy.audit.touched(now)))
        }
    }

    fun deleteMediaCopy(copyId: MediaCopyId) {
        viewModelScope.launch { mediaCopyRepository.deleteCopy(copyId) }
    }

    fun retry() {
        retryTrigger.value += 1
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
