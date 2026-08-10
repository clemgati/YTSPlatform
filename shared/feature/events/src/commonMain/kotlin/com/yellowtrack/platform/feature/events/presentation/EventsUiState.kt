package com.yellowtrack.platform.feature.events.presentation

import com.yellowtrack.platform.core.data.event.IngestStatus
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
    /**
     * Something that went right and is worth saying once.
     *
     * Separate from [problem] because "sent 4 to ada@example.com" is confirmation rather
     * than a fault, and a screen that says both in the same place teaches people to ignore
     * the place.
     */
    val note: String? = null,
    /** True while an action is in flight, so a second tap does not open two stations. */
    val isBusy: Boolean = false,
    /** What each watched folder has done, keyed by source. Empty where nothing is watched. */
    val ingest: Map<String, IngestStatus> = emptyMap(),
    /**
     * False on platforms with no capture folder — a phone is not what a camera shoots into.
     *
     * Asked before the control is drawn rather than discovered when it is pressed. Offering a
     * button that can only fail is worse than not offering it.
     */
    val canWatchFolders: Boolean = false,
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
    /** Everybody signed up, newest first — the list a photographer picks the next name from. */
    val registrations: List<PersonRow> = emptyList(),
    val sittings: List<SittingRow> = emptyList(),
) {
    /**
     * Closed, holding photographs, and not yet handed over.
     *
     * The actual job after an event, and the reason this list is ordered rather than merely
     * displayed: a sitting nobody sends is a person who was photographed and got nothing.
     */
    val awaitingDelivery: List<SittingRow> get() = sittings.filter { it.canDeliver }

    /** Who is in front of each camera now, by station. */
    val seated: Map<String, SittingRow> get() = sittings.filter { it.isOpen }.associateBy { it.stationName }

    /**
     * Sources already claimed, so the form can refuse before the server does.
     *
     * Not a substitute for the 409 — this list is as old as the last refresh, and another
     * photographer on another machine can claim a source between the two. It is here so the
     * common case reads as a form validation rather than as a failed request.
     */
    val claimedSources: Set<String> = stations.filter { it.isOpen }.mapTo(mutableSetOf()) { it.sourceKey }
}

internal data class PersonRow(
    val id: String,
    val email: String,
    val name: String?,
) {
    /** What a photographer reads while somebody stands in front of them. */
    val label: String get() = name?.takeIf { it.isNotBlank() } ?: email
}

internal data class SittingRow(
    val id: String,
    val registrationId: String,
    val email: String,
    val name: String?,
    val stationName: String,
    val closedAt: Long?,
    val deliveredAt: Long?,
    val photographs: Int,
) {
    val isOpen: Boolean get() = closedAt == null
    val isDelivered: Boolean get() = deliveredAt != null

    /** Closed, holding something, and not yet sent. */
    val canDeliver: Boolean get() = !isOpen && !isDelivered && photographs > 0

    val label: String get() = name?.takeIf { it.isNotBlank() } ?: email

    /**
     * Why this sitting cannot be handed over yet, or null when it can.
     *
     * Said rather than left to a disabled button. A photographer looking at a row that will
     * not send needs to know whether to close the station or wait for a photograph.
     */
    val blockedBecause: String?
        get() =
            when {
                isDelivered -> null
                isOpen -> "Still open"
                photographs == 0 -> "No photographs"
                else -> null
            }
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
