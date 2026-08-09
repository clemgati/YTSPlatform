package com.yellowtrack.platform.feature.events.presentation

import com.yellowtrack.platform.core.ui.state.UiState

internal data class EventsUiState(
    val content: UiState<EventsContent>,
    /**
     * The last refusal, which the screen shows and the studio dismisses.
     *
     * Separate from [UiState.Error] because the two mean different things: an error replaces
     * the screen, and this sits on top of a screen that is still correct. "A station is
     * already open on Camera A" must not take away the list of stations that would let
     * somebody close it.
     */
    val problem: String? = null,
    /** True while an action is in flight, so a second tap does not open two stations. */
    val isBusy: Boolean = false,
)

internal data class EventsContent(
    val events: List<EventRow>,
    /**
     * The event being looked at, or null on the list.
     *
     * One field rather than a separate route because an event is a place a studio stays for
     * a day, and coming back to it should not mean finding it in a list again.
     */
    val open: OpenEvent? = null,
)

internal data class EventRow(
    val id: String,
    val name: String,
    val startsAt: Long?,
    val openStations: Int,
    val photographs: Int,
)

internal data class OpenEvent(
    val id: String,
    val name: String,
    val stations: List<StationRow>,
) {
    /**
     * Sources already claimed, so the form can refuse before the server does.
     *
     * Not a substitute for the 409 — this list is as old as the last refresh, and another
     * photographer on another machine can claim a source between the two. It is here so the
     * common case reads as a form validation rather than as a failed request.
     */
    val claimedSources: Set<String> = stations.filter { it.isOpen }.mapTo(mutableSetOf()) { it.sourceKey }
}

internal data class StationRow(
    val id: String,
    val name: String,
    val sourceKey: String,
    val openedAt: Long,
    val closedAt: Long?,
) {
    val isOpen: Boolean get() = closedAt == null
}
