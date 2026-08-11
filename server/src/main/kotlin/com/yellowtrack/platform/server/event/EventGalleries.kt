package com.yellowtrack.platform.server.event

import com.yellowtrack.platform.server.Database
import com.yellowtrack.platform.server.storage.ObjectStore
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Somebody's own photographs, as the page shows them. */
data class Gallery(
    val eventName: String,
    /** Temporary URLs, oldest first. */
    val photographs: List<String>,
)

/**
 * What somebody sees when they follow the link in their email.
 *
 * The second public surface, and it carries more than the first: sign-up reveals an event's
 * name, this reveals photographs of a person. Three things follow.
 *
 * **Only delivered sittings appear.** A valid token does not entitle its holder to
 * photographs the studio has not released — otherwise the hold ADR 0013 describes would be
 * defeated by the gallery rather than by the email, which is worse, because nobody would be
 * watching for it. A slot being closed is not enough either; `delivered_at` is the studio
 * having looked.
 *
 * **Only this registration's sittings appear.** The token is per person. An event's gallery
 * photographs — the ones with no slot — are a separate thing the studio publishes, and are
 * not reachable here.
 *
 * **The URLs expire.** They are presigned and short-lived, so a link forwarded weeks later
 * shows nothing, while the gallery link itself keeps working for whoever it belongs to.
 */
class EventGalleries(
    private val database: Database,
    private val objects: ObjectStore,
    /**
     * Long enough to look through a sitting, short enough that a forwarded image URL is a
     * dead link rather than a permanent copy.
     */
    private val urlValidFor: Duration = 1.hours,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * @return null when the token is unknown, withdrawn, or has nothing released against it
     *   — all three answer identically, because distinguishing them tells a stranger holding
     *   an old link something about somebody else.
     */
    fun photographs(token: String): Gallery? {
        val holder = resolve(token) ?: return null

        return database.inStudio(holder.studioId) { connection ->
            val eventName =
                connection
                    .prepareStatement(
                        """
                        SELECT e.name FROM event_registration r
                        JOIN event e ON e.id = r.event_id
                        WHERE r.id = ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, holder.registrationId)
                        statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                    } ?: return@inStudio null

            val keys =
                connection
                    .prepareStatement(
                        """
                        SELECT o.object_key
                        FROM event_photo p
                        JOIN event_slot s ON s.id = p.slot_id
                        JOIN stored_object o ON o.id = p.stored_object_id
                        WHERE s.registration_id = ?
                          AND s.delivered_at IS NOT NULL
                        ORDER BY p.captured_at, p.id
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, holder.registrationId)
                        statement.executeQuery().use { rows ->
                            buildList { while (rows.next()) add(rows.getString(1)) }
                        }
                    }

            if (keys.isEmpty()) return@inStudio null

            // A deployment with no bucket cannot sign anything. Null rather than a page of
            // broken images, so the route can say the gallery is unavailable rather than
            // showing somebody an empty one and letting them conclude there are no
            // photographs of them.
            val urls = runCatching { keys.map { objects.temporaryUrl(it, urlValidFor) } }.getOrNull()

            urls?.let { Gallery(eventName = eventName, photographs = it) }
        }
    }

    /**
     * Stops honouring somebody's link.
     *
     * The reason the column exists: an attendee asks to be forgotten, or the studio finds it
     * sent the wrong sitting to the wrong person. The link is in an inbox and cannot be
     * recalled, so refusing to honour it is the only remedy there is.
     *
     * Names the studio, because `event_gallery` carries no policy to do it.
     */
    fun revoke(
        studioId: String,
        registrationId: String,
    ) {
        database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    UPDATE event_gallery SET revoked_at = ?
                    WHERE registration_id = ? AND studio_id = ? AND revoked_at IS NULL
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, now())
                    statement.setString(2, registrationId)
                    statement.setString(3, studioId)
                    statement.executeUpdate()
                }
        }
    }

    // -- Internals -----------------------------------------------------------------------

    private data class Holder(
        val studioId: String,
        val registrationId: String,
    )

    /**
     * Outside every policy, by primary key, returning the two identifiers and nothing else.
     *
     * The studio must still exist. Deleting an account is a mark now and a purge thirty days
     * later, and this went on serving signed URLs to the photographs throughout — anybody
     * holding an emailed link could still fetch the pictures of a studio that had asked to be
     * forgotten. The sign-up code had the same hole; this is the worse half of it, because
     * what is on the other side of this token is the photographs themselves.
     */
    private fun resolve(token: String): Holder? =
        database.unscoped { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT g.studio_id, g.registration_id
                    FROM event_gallery g
                    JOIN studio s ON s.id = g.studio_id
                    WHERE g.token = ?
                      AND g.revoked_at IS NULL
                      AND s.deleted_at IS NULL
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, token)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) Holder(rows.getString(1), rows.getString(2)) else null
                    }
                }
        }
}
