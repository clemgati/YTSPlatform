package com.yellowtrack.platform.feature.events.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.data.event.EventActionFailed
import com.yellowtrack.platform.core.data.event.EventsApi
import com.yellowtrack.platform.core.data.event.IngestPlatform
import com.yellowtrack.platform.core.data.event.IngestService
import com.yellowtrack.platform.core.export.Document
import com.yellowtrack.platform.core.export.DocumentFormat
import com.yellowtrack.platform.core.export.DocumentSink
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.RegistrationSummary
import com.yellowtrack.platform.core.model.event.SittingSummary
import com.yellowtrack.platform.core.model.event.StationSummary
import com.yellowtrack.platform.core.ui.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Running an event, from the studio's side of the room.
 *
 * Events are online-first (ADR 0012), so there is no flow to observe and no local copy to
 * reconcile — every action is a request and the screen is refreshed from the answer.
 *
 * Two rules shape the rest of this class, and both come from where it is used. Somebody is
 * holding a camera, standing next to a queue of people, looking at a laptop on a folding
 * table.
 *
 * **A refusal must not take the screen away.** "A station is already open on Camera A" is
 * shown *over* a list that still works, because the fix is to close that station and the list
 * is the only way to do it. Replacing the screen with an error would remove the remedy along
 * with the problem.
 *
 * **An action in flight blocks another.** Not for tidiness: two taps on Open Station a second
 * apart, on a venue's wifi, is how a source ends up with two stations racing for it — and the
 * loser is a 409 the photographer reads as the application being broken.
 */
internal class EventsViewModel(
    private val api: EventsApi,
    private val ingest: IngestService,
    private val platform: IngestPlatform,
    private val sink: DocumentSink,
) : ViewModel() {
    private val state = MutableStateFlow(EventsUiState(content = UiState.Loading))

    /**
     * The screen's own state, plus whatever the watches are doing.
     *
     * Combined rather than copied in, because a watch outlives this view model: a
     * photographer who leaves the Events tab has not stopped shooting, and the counts must
     * still be right when they come back.
     */
    val uiState: StateFlow<EventsUiState> =
        combine(state, ingest.status) { screen, watches ->
            screen.copy(ingest = watches, canWatchFolders = platform.canWatchFolders)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = EventsUiState(content = UiState.Loading),
        )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    /**
     * Suspends rather than launching, so an action that causes a refresh stays "in flight"
     * until the screen actually shows its result. Launching instead would clear the busy flag
     * while the list was still the old one, and the studio would see the button come back
     * before the event it just created appeared.
     */
    private suspend fun reload() {
        val open =
            state.value.content
                .dataOrNull()
                ?.open

        try {
            val events = api.events().map { it.toRow() }
            // The open event is re-read too, so its station list is not left describing the
            // room as it was ten minutes ago.
            val reopened =
                open?.let {
                    it.copy(
                        stations = api.stations(it.id).map(StationSummary::toRow),
                        registrations = api.registrations(it.id).map(RegistrationSummary::toRow),
                        sittings = api.sittings(it.id).map(SittingSummary::toRow),
                    )
                }

            state.update {
                it.copy(
                    // Never Empty, deliberately.
                    //
                    // `UiState.Empty` renders a message and nothing else, and the only way to
                    // create an event lives on the success screen — so a studio with no
                    // events, which is every new studio, was shown "No events yet" and no way
                    // to add one. A test asserted that state and called it correct.
                    //
                    // An empty list is a normal screen with nothing on it, not a different
                    // kind of screen.
                    content = UiState.Success(EventsContent(events, reopened)),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            state.update { current ->
                // Only a screen with nothing on it becomes an error. A refresh that fails
                // while an event is open leaves what is already there and says so quietly —
                // the studio is mid-event and the stale list is still mostly true.
                if (current.content is UiState.Success) {
                    current.copy(problem = failure.readableMessage())
                } else {
                    current.copy(content = UiState.Error(failure.readableMessage()))
                }
            }
        }
    }

    fun open(eventId: String) {
        val row =
            state.value.content
                .dataOrNull()
                ?.events
                ?.firstOrNull { it.id == eventId } ?: return

        act {
            state.update { current ->
                val content = current.content.dataOrNull() ?: return@update current
                current.copy(content = UiState.Success(content.copy(open = OpenEvent(row.id, row.name, emptyList()))))
            }

            reloadEvent(eventId)
        }
    }

    fun closeEvent() {
        state.update { current ->
            val content = current.content.dataOrNull() ?: return@update current
            current.copy(content = UiState.Success(content.copy(open = null)))
        }
    }

    fun createEvent(
        name: String,
        startsAt: Long? = null,
    ) {
        if (name.isBlank()) {
            state.update { it.copy(problem = "An event needs a name.") }
            return
        }

        act {
            api.createEvent(name.trim(), startsAt)
            reload()
        }
    }

    fun openStation(
        eventId: String,
        name: String,
        sourceKey: String,
    ) {
        if (name.isBlank() || sourceKey.isBlank()) {
            state.update { it.copy(problem = "A station needs a name and a folder to watch.") }
            return
        }

        act {
            api.openStation(eventId, name.trim(), sourceKey.trim())
            reloadEvent(eventId)
        }
    }

    fun closeStation(
        eventId: String,
        stationId: String,
    ) {
        // The watch stops with the station, and stops first. A source whose station has
        // closed still has a folder full of files, and a watch left running would keep
        // sending them — they would route to the event's gallery, because no slot is open,
        // and a sitting's leftovers would quietly become public photographs.
        state.value.content
            .let { it as? UiState.Success }
            ?.data
            ?.open
            ?.stations
            ?.firstOrNull { it.id == stationId }
            ?.let { ingest.stop(it.sourceKey) }

        act {
            api.closeStation(eventId, stationId)
            reloadEvent(eventId)
        }
    }

    /**
     * Asks for a folder and begins watching it for [sourceKey].
     *
     * A cancelled chooser is silent. Somebody who opened the dialog and thought better of it
     * has not encountered a problem, and saying so would train them to ignore the place real
     * problems appear.
     */
    fun watchFolder(
        eventId: String,
        sourceKey: String,
    ) {
        viewModelScope.launch {
            val folder =
                try {
                    platform.chooseFolder()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    state.update { it.copy(problem = failure.message ?: "That folder could not be opened.") }
                    return@launch
                } ?: return@launch

            if (!ingest.watch(eventId, sourceKey, folder)) {
                state.update { it.copy(problem = "${folder.name} is already being watched.") }
            }
        }
    }

    fun stopWatching(sourceKey: String) {
        ingest.stop(sourceKey)
    }

    /**
     * Whether a source is actually being watched, as opposed to merely having a record.
     *
     * The status map deliberately keeps entries after a watch stops, so it cannot answer this
     * — and "did the loop stop" is the question worth asserting, since a watch left running
     * on a closed station sends a sitting's leftovers to the public gallery.
     */
    internal fun isWatchingForTest(sourceKey: String): Boolean = ingest.isWatching(sourceKey)

    /**
     * Seats somebody at a station.
     *
     * The most consequential action on this screen: from here every photograph off that
     * camera belongs to this person until somebody advances again, and a mistap sends one
     * guest's photographs to another. It reloads afterwards rather than assuming it worked,
     * so what the screen shows under a camera is what the server believes.
     */
    fun seat(
        eventId: String,
        stationId: String,
        registrationId: String,
    ) {
        act {
            api.advance(eventId, stationId, registrationId)
            reloadEvent(eventId)
        }
    }

    /**
     * Produces the event's sign-up code and saves it where the studio can print it.
     *
     * One action rather than two, because a code nobody can print is not a code. The file is
     * written through the same sink a call sheet goes through, so "where has my file gone"
     * has one answer per platform rather than two.
     */
    fun printSignUpCode(eventId: String) {
        act {
            val invite = api.invite(eventId)
            val card = api.inviteCard(eventId)

            val saved =
                sink.save(
                    Document(
                        baseName = "sign-up-code",
                        format = DocumentFormat.Html,
                        content = card,
                    ),
                )

            state.update {
                it.copy(
                    note = "Saved to ${saved.location}. Open it and print it.",
                    content = it.content.withInvite(eventId, invite.url),
                )
            }
        }
    }

    /**
     * Stops honouring the code.
     *
     * A banner cannot be recalled, so this is the only way to close a sign-up — and the next
     * code issued is a different one, which is what leaves the old banner dead.
     */
    fun withdrawSignUpCode(eventId: String) {
        act {
            api.revokeInvite(eventId)

            state.update {
                it.copy(
                    note = "That code no longer works.",
                    content = it.content.withInvite(eventId, null),
                )
            }
        }
    }

    /** Hands a sitting to the person in it. */
    fun deliver(
        eventId: String,
        slotId: String,
    ) {
        act {
            val delivered = api.deliver(eventId, slotId)

            state.update {
                it.copy(
                    note =
                        if (delivered.sentNow) {
                            "Sent ${delivered.photographs} photograph(s) to ${delivered.email}."
                        } else {
                            "${delivered.email} already had those."
                        },
                )
            }

            reloadEvent(eventId)
        }
    }

    fun dismissNote() {
        state.update { it.copy(note = null) }
    }

    fun dismissProblem() {
        state.update { it.copy(problem = null) }
    }

    // -- Plumbing ---------------------------------------------------------------------------

    /**
     * Runs one action at a time, and turns a refusal into something shown over the screen
     * rather than instead of it.
     */
    private fun act(block: suspend () -> Unit) {
        if (state.value.isBusy) return

        state.update { it.copy(isBusy = true, problem = null) }

        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                state.update { it.copy(problem = failure.readableMessage()) }
            } finally {
                // In `finally`, or one failed request leaves every button dead for the rest
                // of the event with nothing on screen to say why.
                state.update { it.copy(isBusy = false) }
            }
        }
    }

    /**
     * Stations, sign-ups and sittings together.
     *
     * One reload rather than three, because they are read as one thing: who is at which
     * camera, and what is waiting to be sent. Refreshing them separately would show a station
     * whose sitting had not caught up — which on this screen means the wrong name under a
     * camera, in front of the person it is wrong about.
     */
    private suspend fun reloadEvent(eventId: String) {
        val stations = api.stations(eventId).map(StationSummary::toRow)
        val registrations = api.registrations(eventId).map(RegistrationSummary::toRow)
        val sittings = api.sittings(eventId).map(SittingSummary::toRow)

        state.update { current ->
            val content = current.content.dataOrNull() ?: return@update current
            val open = content.open?.takeIf { it.id == eventId } ?: return@update current

            current.copy(
                content =
                    UiState.Success(
                        content.copy(
                            open = open.copy(stations = stations, registrations = registrations, sittings = sittings),
                        ),
                    ),
            )
        }
    }

    private fun Throwable.readableMessage(): String =
        when (this) {
            is EventActionFailed -> message ?: FALLBACK
            else -> FALLBACK
        }

    private fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Success)?.data

    private fun UiState<EventsContent>.withInvite(
        eventId: String,
        url: String?,
    ): UiState<EventsContent> {
        val content = dataOrNull() ?: return this
        val open = content.open?.takeIf { it.id == eventId } ?: return this

        return UiState.Success(content.copy(open = open.copy(inviteUrl = url)))
    }

    private companion object {
        const val FALLBACK = "That could not be done just now."
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun EventSummary.toRow() = EventRow(id, name, startsAt, openStations, photographs)

private fun StationSummary.toRow() = StationRow(id, name, sourceKey, openedAt, closedAt)

private fun RegistrationSummary.toRow() = PersonRow(id, email, name)

private fun SittingSummary.toRow() =
    SittingRow(
        id = id,
        registrationId = registrationId,
        email = email,
        name = name,
        stationName = stationName,
        closedAt = closedAt,
        deliveredAt = deliveredAt,
        photographs = photographs,
    )
