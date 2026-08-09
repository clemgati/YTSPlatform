package com.yellowtrack.platform.feature.events.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.data.event.EventActionFailed
import com.yellowtrack.platform.core.data.event.EventsApi
import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.StationSummary
import com.yellowtrack.platform.core.ui.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {
    private val state = MutableStateFlow(EventsUiState(content = UiState.Loading))
    val uiState: StateFlow<EventsUiState> = state.asStateFlow()

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
            val reopened = open?.let { OpenEvent(it.id, it.name, api.stations(it.id).map(StationSummary::toRow)) }

            state.update {
                it.copy(
                    content =
                        if (events.isEmpty() && reopened == null) {
                            UiState.Empty
                        } else {
                            UiState.Success(EventsContent(events, reopened))
                        },
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
            val stations = api.stations(eventId).map(StationSummary::toRow)

            state.update { current ->
                val content = current.content.dataOrNull() ?: return@update current
                current.copy(content = UiState.Success(content.copy(open = OpenEvent(row.id, row.name, stations))))
            }
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
            reloadStations(eventId)
        }
    }

    fun closeStation(
        eventId: String,
        stationId: String,
    ) {
        act {
            api.closeStation(eventId, stationId)
            reloadStations(eventId)
        }
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

    private suspend fun reloadStations(eventId: String) {
        val stations = api.stations(eventId).map(StationSummary::toRow)

        state.update { current ->
            val content = current.content.dataOrNull() ?: return@update current
            val open = content.open?.takeIf { it.id == eventId } ?: return@update current

            current.copy(content = UiState.Success(content.copy(open = open.copy(stations = stations))))
        }
    }

    private fun Throwable.readableMessage(): String =
        when (this) {
            is EventActionFailed -> message ?: FALLBACK
            else -> FALLBACK
        }

    private fun <T> UiState<T>.dataOrNull(): T? = (this as? UiState.Success)?.data

    private companion object {
        const val FALLBACK = "That could not be done just now."
    }
}

private fun EventSummary.toRow() = EventRow(id, name, startsAt, openStations, photographs)

private fun StationSummary.toRow() = StationRow(id, name, sourceKey, openedAt, closedAt)
