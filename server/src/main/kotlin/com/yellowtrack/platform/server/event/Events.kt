package com.yellowtrack.platform.server.event

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
                    statement.executeUpdate()
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
