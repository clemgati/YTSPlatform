package com.yellowtrack.platform.server.event

import com.yellowtrack.platform.core.model.event.EventSummary
import com.yellowtrack.platform.core.model.event.RegistrationSummary
import com.yellowtrack.platform.core.model.event.SittingSummary
import com.yellowtrack.platform.core.model.event.StationSummary
import com.yellowtrack.platform.server.Database
import java.sql.Connection
import java.util.UUID

/** Where a photograph ended up, and why. */
sealed interface Routed {
    /** It belongs to one person, because a slot was open on the source it arrived from. */
    data class ToSlot(
        val photoId: String,
        val slotId: String,
        val registrationId: String,
    ) : Routed

    /** It belongs to the event, which is the default and the whole of a roaming event. */
    data class ToGallery(
        val photoId: String,
    ) : Routed
}

/**
 * Two stations cannot hold one source at the same time.
 *
 * Enforced by a partial unique index rather than by a check, because a check would be a race
 * — two photographers opening a station on the same camera within the same second would both
 * find it free. The index is scoped to the studio, so two studios may each name a folder
 * "Camera A" without colliding.
 *
 * A distinct type rather than a raw constraint violation, so the route can answer 409 with
 * something a photographer can act on instead of 500.
 */
class SourceAlreadyInUse(
    val sourceKey: String,
) : Exception("a station is already open on $sourceKey")

/**
 * Why a station could not be advanced to somebody.
 *
 * Both of these were possible and unchecked until the routes went in: nothing stopped a slot
 * being opened on a station that had finished, or bound to somebody registered for a
 * different event entirely. Neither shows up as an error — the slot is created, photographs
 * route into it, and the wrong person is emailed.
 */
sealed class AdvanceRefused(
    message: String,
) : Exception(message) {
    data object NoSuchStation : AdvanceRefused("That station is not there.")

    /** A finished station must not take another sitting. */
    data object StationClosed : AdvanceRefused("That station is closed. Open it again first.")

    /** Registered for another event, or not registered at all. */
    data object NoSuchRegistration : AdvanceRefused("Nobody by that registration is signed up to this event.")
}

/**
 * Events, stations, slots, and the one question that decides who a photograph belongs to.
 *
 * ADR 0013 decision 4. Every event has a gallery. A station is a period a photographer opens
 * inside it and closes again, bound to an ingest source. When a photograph arrives, this asks
 * *was a slot open on the source it came from* — and that is the whole of the routing.
 *
 * ## Why the answer is computed here rather than sent
 *
 * The alternative is a device telling the server which slot a photograph belongs to. That
 * would put the routing decision on whichever machine happened to be watching the folder, at
 * the moment it noticed a file — which is after the shutter, by an unbounded amount, on a
 * laptop that may have been asleep. The server holds when each slot opened and closed, so it
 * can answer from the capture time rather than from the upload time.
 *
 * ## What this deliberately does not do
 *
 * Deliver anything. A slot's photographs are held until the slot is closed and the studio has
 * seen them, because a mis-advanced slot sends one person's headshot to another and that is a
 * privacy incident rather than a glitch.
 */
class Events(
    private val database: Database,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun createEvent(
        studioId: String,
        name: String,
        startsAt: Long? = null,
    ): String =
        database.inStudio(studioId) { connection ->
            val id = newId()
            connection
                .prepareStatement(
                    """
                    INSERT INTO event(id, studio_id, name, starts_at, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, studioId)
                    statement.setString(3, name)
                    startsAt?.let { statement.setLong(4, it) } ?: statement.setNull(4, java.sql.Types.BIGINT)
                    statement.setLong(5, now())
                    statement.setLong(6, now())
                    statement.executeUpdate()
                }
            id
        }

    /**
     * Signs somebody up, or returns the registration they already have.
     *
     * Idempotent on the address, because somebody who scans the QR code twice is one person
     * and two registrations would mean two half-galleries.
     */
    fun register(
        studioId: String,
        eventId: String,
        email: String,
        name: String? = null,
    ): String =
        database.inStudio(studioId) { connection ->
            existingRegistration(connection, eventId, email) ?: newId().also { id ->
                connection
                    .prepareStatement(
                        """
                        INSERT INTO event_registration(id, studio_id, event_id, email, name, registered_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, id)
                        statement.setString(2, studioId)
                        statement.setString(3, eventId)
                        statement.setString(4, email)
                        statement.setString(5, name)
                        statement.setLong(6, now())
                        statement.executeUpdate()
                    }
            }
        }

    /** Opens a station bound to [sourceKey]. One camera, one station at a time. */
    fun openStation(
        studioId: String,
        eventId: String,
        name: String,
        sourceKey: String,
    ): String =
        database.inStudio(studioId) { connection ->
            val id = newId()
            connection
                .prepareStatement(
                    """
                    INSERT INTO event_station(id, studio_id, event_id, name, source_key, opened_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, studioId)
                    statement.setString(3, eventId)
                    statement.setString(4, name)
                    statement.setString(5, sourceKey)
                    statement.setLong(6, now())
                    try {
                        statement.executeUpdate()
                    } catch (violation: java.sql.SQLException) {
                        // 23505 is unique_violation. Only this index can raise it here, and
                        // it means a station is already open on the source.
                        if (violation.sqlState == "23505") throw SourceAlreadyInUse(sourceKey)
                        throw violation
                    }
                }
            id
        }

    /** Closes the station, so its source returns to the gallery. */
    fun closeStation(
        studioId: String,
        stationId: String,
    ) {
        database.inStudio(studioId) { connection ->
            closeOpenSlot(connection, stationId)
            connection
                .prepareStatement("UPDATE event_station SET closed_at = ? WHERE id = ? AND closed_at IS NULL")
                .use { statement ->
                    statement.setLong(1, now())
                    statement.setString(2, stationId)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * Gives [registrationId] the station's slot, closing whoever had it.
     *
     * Advancing is one action rather than a close and an open, because the two happening
     * separately is how a photograph lands in the gap between subjects and belongs to
     * neither.
     */
    fun advanceSlot(
        studioId: String,
        stationId: String,
        registrationId: String,
    ): String =
        database.inStudio(studioId) { connection ->
            val eventId = eventOfOpenStation(connection, stationId)
            if (!registeredForEvent(connection, eventId, registrationId)) throw AdvanceRefused.NoSuchRegistration

            closeOpenSlot(connection, stationId)

            val id = newId()
            connection
                .prepareStatement(
                    """
                    INSERT INTO event_slot(id, studio_id, station_id, registration_id, opened_at)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, studioId)
                    statement.setString(3, stationId)
                    statement.setString(4, registrationId)
                    statement.setLong(5, now())
                    statement.executeUpdate()
                }
            id
        }

    /**
     * Records a photograph, and decides whose it is.
     *
     * [capturedAt] rather than the time this runs. A folder watcher notices a file some time
     * after the shutter, and on a laptop that woke up late the difference can be minutes —
     * long enough to have advanced past the person actually in the frame.
     */
    fun recordPhotograph(
        studioId: String,
        eventId: String,
        sourceKey: String,
        storedObjectId: String,
        capturedAt: Long,
    ): Routed =
        database.inStudio(studioId) { connection ->
            val slot = slotOpenAt(connection, eventId, sourceKey, capturedAt)
            val photoId = newId()

            connection
                .prepareStatement(
                    """
                    INSERT INTO event_photo(id, studio_id, event_id, stored_object_id, slot_id, captured_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, photoId)
                    statement.setString(2, studioId)
                    statement.setString(3, eventId)
                    statement.setString(4, storedObjectId)
                    statement.setString(5, slot?.slotId)
                    statement.setLong(6, capturedAt)
                    statement.executeUpdate()
                }

            slot
                ?.let { Routed.ToSlot(photoId, it.slotId, it.registrationId) }
                ?: Routed.ToGallery(photoId)
        }

    /** Publishes gallery photographs, which is the studio's decision rather than the camera's. */
    fun publish(
        studioId: String,
        photoIds: List<String>,
    ): Int =
        database.inStudio(studioId) { connection ->
            var published = 0
            connection
                .prepareStatement("UPDATE event_photo SET published_at = ? WHERE id = ? AND published_at IS NULL")
                .use { statement ->
                    photoIds.forEach { id ->
                        statement.setLong(1, now())
                        statement.setString(2, id)
                        published += statement.executeUpdate()
                    }
                }
            published
        }

    // -- Reading ---------------------------------------------------------------------------

    /** Who has signed up, most recent first. The list a photographer picks a name from. */
    fun listRegistrations(
        studioId: String,
        eventId: String,
    ): List<RegistrationSummary> =
        database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT id, email, name, registered_at
                    FROM event_registration
                    WHERE event_id = ?
                    ORDER BY registered_at DESC, id
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, eventId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    RegistrationSummary(
                                        id = rows.getString(1),
                                        email = rows.getString(2),
                                        name = rows.getString(3),
                                        registeredAt = rows.getLong(4),
                                    ),
                                )
                            }
                        }
                    }
                }
        }

    /**
     * The event's sittings, newest first, with what each is waiting for.
     *
     * The list the studio works down after an event: who, how many photographs, closed or
     * not, delivered or not. Undelivered closed sittings are the whole job.
     */
    fun listSittings(
        studioId: String,
        eventId: String,
    ): List<SittingSummary> =
        database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT s.id, s.registration_id, r.email, r.name, t.name,
                           s.opened_at, s.closed_at, s.delivered_at,
                           (SELECT count(*) FROM event_photo p WHERE p.slot_id = s.id)
                    FROM event_slot s
                    JOIN event_station t ON t.id = s.station_id
                    JOIN event_registration r ON r.id = s.registration_id
                    WHERE t.event_id = ?
                    ORDER BY s.opened_at DESC, s.id
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, eventId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    SittingSummary(
                                        id = rows.getString(1),
                                        registrationId = rows.getString(2),
                                        email = rows.getString(3),
                                        name = rows.getString(4),
                                        stationName = rows.getString(5),
                                        openedAt = rows.getLong(6),
                                        closedAt = rows.getLong(7).takeUnless { rows.wasNull() },
                                        deliveredAt = rows.getLong(8).takeUnless { rows.wasNull() },
                                        photographs = rows.getInt(9),
                                    ),
                                )
                            }
                        }
                    }
                }
        }

    /**
     * The studio's events, most recent first.
     *
     * The counts are subqueries rather than a second round trip because the open-station
     * count is the one thing the list must not be stale about: a station left open after
     * everybody has gone home keeps claiming photographs for whoever was last in front of
     * the camera.
     */
    fun listEvents(studioId: String): List<EventSummary> =
        database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT e.id,
                           e.name,
                           e.starts_at,
                           (SELECT count(*) FROM event_station s
                             WHERE s.event_id = e.id AND s.closed_at IS NULL),
                           (SELECT count(*) FROM event_photo p WHERE p.event_id = e.id)
                    FROM event e
                    ORDER BY coalesce(e.starts_at, e.created_at) DESC, e.id
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                val startsAt = rows.getLong(3).takeUnless { rows.wasNull() }
                                add(
                                    EventSummary(
                                        id = rows.getString(1),
                                        name = rows.getString(2),
                                        startsAt = startsAt,
                                        openStations = rows.getInt(4),
                                        photographs = rows.getInt(5),
                                    ),
                                )
                            }
                        }
                    }
                }
        }

    /** The event's stations, open ones first — those are the ones a photographer acts on. */
    fun listStations(
        studioId: String,
        eventId: String,
    ): List<StationSummary> =
        database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT id, name, source_key, opened_at, closed_at
                    FROM event_station
                    WHERE event_id = ?
                    ORDER BY closed_at IS NOT NULL, opened_at DESC, id
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, eventId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                val closedAt = rows.getLong(5).takeUnless { rows.wasNull() }
                                add(
                                    StationSummary(
                                        id = rows.getString(1),
                                        name = rows.getString(2),
                                        sourceKey = rows.getString(3),
                                        openedAt = rows.getLong(4),
                                        closedAt = closedAt,
                                    ),
                                )
                            }
                        }
                    }
                }
        }

    // -- Internals -----------------------------------------------------------------------

    private data class OpenSlot(
        val slotId: String,
        val registrationId: String,
    )

    /**
     * The slot that held [instant] on this source, if any.
     *
     * Bounded at both ends rather than "the currently open slot", so a photograph uploaded
     * after the photographer has moved on still belongs to whoever was in front of the camera
     * when it was taken.
     */
    private fun slotOpenAt(
        connection: Connection,
        eventId: String,
        sourceKey: String,
        instant: Long,
    ): OpenSlot? =
        connection
            .prepareStatement(
                """
                SELECT s.id, s.registration_id
                FROM event_slot s
                JOIN event_station t ON t.id = s.station_id
                WHERE t.event_id = ?
                  AND t.source_key = ?
                  AND s.opened_at <= ?
                  AND (s.closed_at IS NULL OR s.closed_at >= ?)
                ORDER BY s.opened_at DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, eventId)
                statement.setString(2, sourceKey)
                statement.setLong(3, instant)
                statement.setLong(4, instant)
                statement.executeQuery().use { rows ->
                    if (rows.next()) OpenSlot(rows.getString(1), rows.getString(2)) else null
                }
            }

    /** The event a station belongs to, refusing if it is gone or finished. */
    private fun eventOfOpenStation(
        connection: Connection,
        stationId: String,
    ): String =
        connection
            .prepareStatement("SELECT event_id, closed_at FROM event_station WHERE id = ?")
            .use { statement ->
                statement.setString(1, stationId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) throw AdvanceRefused.NoSuchStation
                    val eventId = rows.getString(1)
                    rows.getLong(2).takeUnless { rows.wasNull() }?.let { throw AdvanceRefused.StationClosed }
                    eventId
                }
            }

    /**
     * Somebody signed up to *this* event.
     *
     * Row level security already keeps another studio's registrations invisible. This is the
     * narrower question it cannot answer: a studio running two events on one day must not be
     * able to seat a person from the morning into the afternoon's station, which would send
     * them somebody else's sitting.
     */
    private fun registeredForEvent(
        connection: Connection,
        eventId: String,
        registrationId: String,
    ): Boolean =
        connection
            .prepareStatement("SELECT 1 FROM event_registration WHERE id = ? AND event_id = ?")
            .use { statement ->
                statement.setString(1, registrationId)
                statement.setString(2, eventId)
                statement.executeQuery().use { it.next() }
            }

    private fun existingRegistration(
        connection: Connection,
        eventId: String,
        email: String,
    ): String? =
        connection
            .prepareStatement("SELECT id FROM event_registration WHERE event_id = ? AND email = ?")
            .use { statement ->
                statement.setString(1, eventId)
                statement.setString(2, email)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }

    private fun closeOpenSlot(
        connection: Connection,
        stationId: String,
    ) {
        connection
            .prepareStatement("UPDATE event_slot SET closed_at = ? WHERE station_id = ? AND closed_at IS NULL")
            .use { statement ->
                statement.setLong(1, now())
                statement.setString(2, stationId)
                statement.executeUpdate()
            }
    }
}
