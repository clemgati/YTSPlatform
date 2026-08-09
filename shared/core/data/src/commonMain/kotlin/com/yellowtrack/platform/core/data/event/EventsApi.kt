package com.yellowtrack.platform.core.data.event

import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.StationSummary

/**
 * Something the studio can do about it.
 *
 * Every refusal on this path is actionable — a source already in use, a name left blank, a
 * server that cannot be reached mid-event — so the server's own words are carried through
 * rather than replaced with "that did not work". A photographer standing at a camera needs
 * to know it is *this* camera that is already claimed.
 */
class EventActionFailed(
    message: String,
) : Exception(message)

/**
 * Running an event, from the studio's side.
 *
 * Events are online-first (ADR 0012): there is no local table, no outbox and no
 * reconciliation. An event is a thing several people are looking at from several machines in
 * one room, and a local copy that diverged would mean two photographers disagreeing about
 * which station is open — which decides who a photograph belongs to.
 *
 * The cost is honest and stated: with no network there is no event. That is the right trade
 * for a feature whose entire premise is delivering photographs to people while they are still
 * standing there.
 */
interface EventsApi {
    suspend fun events(): List<EventSummary>

    suspend fun createEvent(
        name: String,
        startsAt: Long? = null,
    ): String

    suspend fun stations(eventId: String): List<StationSummary>

    /** @throws EventActionFailed when the source already carries an open station. */
    suspend fun openStation(
        eventId: String,
        name: String,
        sourceKey: String,
    ): String

    /** Idempotent, as the route is: the state being asked for is "closed". */
    suspend fun closeStation(
        eventId: String,
        stationId: String,
    )
}
