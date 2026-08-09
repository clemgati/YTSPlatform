package com.yellowtrack.platform.server.event

import com.yellowtrack.platform.server.Database
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** An event as somebody scanning its code is allowed to see it. */
data class InvitedEvent(
    val studioId: String,
    val eventId: String,
    val eventName: String,
)

/** Why a sign-up was not accepted. */
sealed interface SignUpRefused {
    /** The token is unknown, or the studio has withdrawn it. */
    data object NoSuchInvite : SignUpRefused

    /** Not a usable address, so nothing could ever be delivered to it. */
    data object BadAddress : SignUpRefused

    /**
     * This event has taken more sign-ups in the window than any real event produces.
     *
     * Deliberately not "you are going too fast" — the caller here is a guest with a phone,
     * and the limit is on the event rather than on them.
     */
    data object TooManyForNow : SignUpRefused
}

/**
 * The public half of an event: a token, and what somebody holding it may do.
 *
 * This is the first thing in the application an unauthenticated caller can reach, so what it
 * deliberately does *not* do matters as much as what it does.
 *
 * ## The token is the whole of the secret
 *
 * 128 bits from [SecureRandom], base64url so it survives a URL and a QR code. Guessing one is
 * not a threat model worth further mitigation; holding one tells you nothing about any other
 * event, any other studio, or anything about this studio beyond the event's name.
 *
 * ## The lookup runs outside row level security, and has to
 *
 * `event_invite` carries no policy, because a policy keyed on `app.studio_id` cannot guard
 * the query that decides what `app.studio_id` should be — the same hole the authentication
 * tables have, and the same reason (ADR 0009 decision 7). It is kept narrow the same way:
 * one query, `WHERE token = ?`, returning one row. Everything afterwards runs inside
 * [Database.inStudio] like any other work.
 *
 * ## Signing up says the same thing twice
 *
 * Registering an address that is already registered succeeds, exactly as registering a new
 * one does, and the answer is identical. Anything else turns a public endpoint into a way of
 * asking whether a particular person attended a particular event.
 */
class EventInvites(
    private val database: Database,
    private val events: Events = Events(database),
    private val now: () -> Long = System::currentTimeMillis,
    private val newToken: () -> String = ::randomToken,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /**
     * The most sign-ups one event may take in [window].
     *
     * A cap on the event rather than on the caller: behind a venue's shared wifi or a mobile
     * network everybody arrives from a handful of addresses, so a per-caller limit would
     * refuse a real queue while barely inconveniencing a script. Set well above what a real
     * event produces — a conference of a thousand people does not sign up a thousand times in
     * ten minutes — so it bounds abuse without ever being met by a genuine crowd.
     */
    private val limit: Int = DEFAULT_LIMIT,
    private val window: Long = DEFAULT_WINDOW_MILLIS,
) {
    /**
     * Issues the event's invite, or returns the one it already has.
     *
     * Idempotent, because a studio pressing the button twice wants one code rather than two —
     * and because the second code would silently orphan whichever banner was printed from the
     * first.
     */
    fun issue(
        studioId: String,
        eventId: String,
    ): String? =
        database.inStudio(studioId) { connection ->
            // The event has to be this studio's, and nothing else here checks that.
            //
            // `event_invite` carries no policy, and a foreign key check is not filtered by
            // one either — so without this a studio could insert an invite pointing at
            // another studio's event. The token would be useless to them, because the lookup
            // re-enters *their* scope and finds nothing, but the row would occupy the one
            // live invite slot for that event and the studio that owns it could never issue
            // its own. This SELECT is inside the studio scope, so row level security answers
            // the question.
            if (!ownsEvent(connection, eventId)) return@inStudio null

            existingToken(connection, studioId, eventId) ?: newToken().also { token ->
                connection
                    .prepareStatement(
                        """
                        INSERT INTO event_invite(token, studio_id, event_id, created_at)
                        VALUES (?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, token)
                        statement.setString(2, studioId)
                        statement.setString(3, eventId)
                        statement.setLong(4, now())
                        statement.executeUpdate()
                    }
            }
        }

    /** Withdraws the event's invite. The printed code stops working; nothing else changes. */
    fun revoke(
        studioId: String,
        eventId: String,
    ) {
        database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    UPDATE event_invite SET revoked_at = ?
                    WHERE event_id = ? AND studio_id = ? AND revoked_at IS NULL
                    """.trimIndent(),
                ).use { statement ->
                    statement.setLong(1, now())
                    statement.setString(2, eventId)
                    statement.setString(3, studioId)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * What the sign-up page may show: the event's name, and nothing else.
     *
     * Not the photograph count, not the stations, not the studio's other events. Somebody
     * scanning a code needs to know they are signing up to the right thing, and that is the
     * whole of the requirement.
     */
    fun lookUp(token: String): InvitedEvent? {
        val invite = resolve(token) ?: return null

        return database.inStudio(invite.studioId) { connection ->
            connection
                .prepareStatement("SELECT name FROM event WHERE id = ? AND deleted_at IS NULL")
                .use { statement ->
                    statement.setString(1, invite.eventId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) {
                            InvitedEvent(invite.studioId, invite.eventId, rows.getString(1))
                        } else {
                            null
                        }
                    }
                }
        }
    }

    /**
     * Signs somebody up, or does nothing and says the same thing.
     *
     * @return null on success. A refusal is a [SignUpRefused], and "you are already signed
     *   up" is deliberately not one of them.
     */
    fun signUp(
        token: String,
        email: String,
        name: String? = null,
    ): SignUpRefused? {
        val invited = lookUp(token) ?: return SignUpRefused.NoSuchInvite
        val address = email.trim()

        if (!looksLikeAnAddress(address)) return SignUpRefused.BadAddress

        return database.inStudio(invited.studioId) { connection ->
            if (recentSignUps(connection, invited.eventId) >= limit) {
                return@inStudio SignUpRefused.TooManyForNow
            }

            // `register` is idempotent on the address and returns the existing registration
            // when there is one, so this is the same call either way — which is what makes
            // the answer the same either way.
            events.register(invited.studioId, invited.eventId, address, name?.trim()?.takeIf { it.isNotBlank() })

            null
        }
    }

    // -- Internals -----------------------------------------------------------------------

    private data class Invite(
        val studioId: String,
        val eventId: String,
    )

    /**
     * Token to studio, outside every policy.
     *
     * The only query in the application that reads a table with no row level security and is
     * reachable without a session. It selects by primary key, returns at most one row, and
     * exposes nothing but the two identifiers needed to re-enter a studio scope.
     */
    private fun resolve(token: String): Invite? =
        database.unscoped { connection ->
            connection
                .prepareStatement("SELECT studio_id, event_id FROM event_invite WHERE token = ? AND revoked_at IS NULL")
                .use { statement ->
                    statement.setString(1, token)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) Invite(rows.getString(1), rows.getString(2)) else null
                    }
                }
        }

    /** Guarded by the policy on `event`, which is the point of asking here rather than there. */
    private fun ownsEvent(
        connection: java.sql.Connection,
        eventId: String,
    ): Boolean =
        connection
            .prepareStatement("SELECT 1 FROM event WHERE id = ? AND deleted_at IS NULL")
            .use { statement ->
                statement.setString(1, eventId)
                statement.executeQuery().use { rows -> rows.next() }
            }

    private fun existingToken(
        connection: java.sql.Connection,
        studioId: String,
        eventId: String,
    ): String? =
        connection
            .prepareStatement(
                "SELECT token FROM event_invite WHERE event_id = ? AND studio_id = ? AND revoked_at IS NULL",
            ).use { statement ->
                statement.setString(1, eventId)
                statement.setString(2, studioId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }

    private fun recentSignUps(
        connection: java.sql.Connection,
        eventId: String,
    ): Int =
        connection
            .prepareStatement("SELECT count(*) FROM event_registration WHERE event_id = ? AND registered_at >= ?")
            .use { statement ->
                statement.setString(1, eventId)
                statement.setLong(2, now() - window)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }

    companion object {
        /** Generous against a real crowd, and a wall against a script. */
        const val DEFAULT_LIMIT = 500
        const val DEFAULT_WINDOW_MILLIS = 10 * 60 * 1000L

        private val random = SecureRandom()

        /**
         * 128 bits, base64url without padding.
         *
         * URL-safe because it travels in a path and inside a QR code, and unpadded because
         * `=` is the character most likely to be mangled by whatever prints the banner.
         */
        fun randomToken(): String {
            val bytes = ByteArray(16)
            random.nextBytes(bytes)

            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        /**
         * Enough to reject what can never be delivered, and no more.
         *
         * Address syntax is not a thing worth being clever about — the only proof an address
         * works is a message arriving at it, which is the next piece of work. This rejects
         * the obviously impossible so a typo is caught while somebody is still standing
         * there to correct it.
         */
        fun looksLikeAnAddress(value: String): Boolean {
            val at = value.indexOf('@')

            return at > 0 &&
                at == value.lastIndexOf('@') &&
                at < value.length - 1 &&
                value.substringAfter('@').contains('.') &&
                !value.endsWith('.') &&
                value.none { it.isWhitespace() }
        }
    }
}
