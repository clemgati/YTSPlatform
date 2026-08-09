package com.yellowtrack.platform.core.model.event

import kotlinx.serialization.Serializable

/**
 * The envelopes the studio's application and the server share.
 *
 * ADR 0007: one definition compiled into both sides, so a change to either is a build failure
 * rather than a field that quietly stops crossing.
 *
 * Events are online-first (ADR 0012) and so are not `core:model` entities in the way a client
 * or a session is — there is no local table, no outbox and no version. These are what the
 * wire carries and nothing more.
 */
@Serializable
data class CreateEventRequest(
    val name: String,
    /** Null for an event with no announced start, which a walk-up event has. */
    val startsAt: Long? = null,
)

/** An event as the studio's list shows it. */
@Serializable
data class EventSummary(
    val id: String,
    val name: String,
    val startsAt: Long? = null,
    /**
     * Stations currently open on this event.
     *
     * On the summary because it is the one thing a studio needs to see without opening an
     * event: a station left open after everyone has gone home keeps routing photographs to
     * somebody who is no longer in front of the camera.
     */
    val openStations: Int = 0,
    val photographs: Int = 0,
)

@Serializable
data class OpenStationRequest(
    val name: String,
    /**
     * The ingest source — in practice the watched folder's name, and through it one camera.
     *
     * The binding a photograph is routed by. Two stations cannot hold the same source at
     * once, because a photograph arriving on it could then belong to either.
     */
    val sourceKey: String,
)

/** A station, open or finished. */
@Serializable
data class StationSummary(
    val id: String,
    val name: String,
    val sourceKey: String,
    val openedAt: Long,
    /** Null while it is still open. */
    val closedAt: Long? = null,
)

/** What a create returned, when the caller needs the identifier and nothing else. */
@Serializable
data class CreatedResponse(
    val id: String,
)
