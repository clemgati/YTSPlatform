package com.yellowtrack.platform.server.event

import com.yellowtrack.platform.server.Database
import com.yellowtrack.platform.server.mail.Email
import com.yellowtrack.platform.server.mail.Mailer
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
     * One or both halves of the name are missing.
     *
     * Distinct from [BadAddress] because the guest can fix it and needs telling which field
     * to fix. Not a secret either: it says nothing about the event or about anybody at it.
     */
    data object MissingName : SignUpRefused

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
    /**
     * Sends the note that says somebody is on the list. Null sends nothing.
     *
     * Null by default so a deployment with no mail configured still takes sign-ups, and so a
     * test about who is registered does not have to care about mail. A guest who is on the
     * list and did not get a note is a guest who is on the list; a guest turned away because
     * the mail server was down is a guest who is not.
     */
    private val mailer: Mailer? = null,
    private val fromAddress: String? = null,
    private val onSendFailure: (Throwable) -> Unit = {},
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
        givenName: String,
        familyName: String,
        phone: String? = null,
    ): SignUpRefused? {
        val invited = lookUp(token) ?: return SignUpRefused.NoSuchInvite
        val address = email.trim()
        val given = givenName.trim()
        val family = familyName.trim()

        if (!looksLikeAnAddress(address)) return SignUpRefused.BadAddress

        // Both parts, because a queue is seated by name and half a name does not seat
        // anybody. The form asks for them; this refuses without them, since the form is not
        // the only thing that can post here.
        if (given.isBlank() || family.isBlank()) return SignUpRefused.MissingName

        return database.inStudio(invited.studioId) { connection ->
            if (recentSignUps(connection, invited.eventId) >= limit) {
                return@inStudio SignUpRefused.TooManyForNow
            }

            // `register` is idempotent on the address and returns the existing registration
            // when there is one, so this is the same call either way — which is what makes
            // the answer the same either way.
            val registration =
                events.register(
                    studioId = invited.studioId,
                    eventId = invited.eventId,
                    email = address,
                    name = "$given $family",
                    givenName = given,
                    familyName = family,
                    phone = phone?.trim()?.takeIf { it.isNotBlank() },
                )

            // Only the first time. A second scan of the same code by the same person is not
            // somebody joining, and sending the same welcome twice teaches them to ignore it.
            if (registration.isNew) {
                confirm(connection, invited, address, given, registration.number)
            }

            null
        }
    }

    /**
     * Tells somebody they are on the list, and what their number is.
     *
     * Sent because a guest who scans a code and sees a page for two seconds has no record of
     * having done it — and because the number is only useful to them if they have it. The
     * photographer calling "John Smith, four one eight two two" needs the person to recognise
     * it.
     *
     * Failure is logged and swallowed. The registration is the thing that matters; a note
     * that did not arrive is a nuisance, and refusing the sign-up because the mail server was
     * unreachable would turn it into somebody standing at a table unable to join.
     */
    private fun confirm(
        connection: java.sql.Connection,
        invited: InvitedEvent,
        email: String,
        givenName: String,
        number: Int?,
    ) {
        val mailer = mailer ?: return
        val from = fromAddress ?: return
        val studio = studioDetails(connection, invited.studioId) ?: return
        val replyTo = studio.email?.takeIf { it.isNotBlank() } ?: return

        runCatching {
            mailer.send(
                Email(
                    to = email,
                    subject = "You are signed up for ${invited.eventName}",
                    body = confirmationText(invited.eventName, studio.name, givenName, number),
                    html = confirmationHtml(invited.eventName, studio.name, givenName, number),
                    fromName = studio.name,
                    fromAddress = from,
                    replyTo = replyTo,
                    headers = mapOf("List-Unsubscribe" to "<mailto:$replyTo?subject=Unsubscribe>"),
                ),
            )
        }.onFailure(onSendFailure)
    }

    private data class StudioDetails(
        val name: String,
        val email: String?,
    )

    private fun studioDetails(
        connection: java.sql.Connection,
        studioId: String,
    ): StudioDetails? =
        connection
            .prepareStatement("SELECT name, email FROM studio_profile WHERE studio_id = ? AND deleted_at IS NULL")
            .use { statement ->
                statement.setString(1, studioId)
                statement.executeQuery().use { rows ->
                    if (rows.next()) StudioDetails(rows.getString(1), rows.getString(2)) else null
                }
            }

    /**
     * Enough words to be a message rather than a receipt.
     *
     * The same lesson the delivery mail learned: a short note whose only content is a number
     * is the shape of something a filter distrusts. Naming the event, the studio, and where
     * the address came from tells a filter and a person the same true thing.
     */
    private fun confirmationText(
        eventName: String,
        studioName: String,
        givenName: String,
        number: Int?,
    ): String =
        buildString {
            appendLine("Hello $givenName,")
            appendLine()
            appendLine(
                "You scanned a code at $eventName and asked $studioName to send you your " +
                    "photographs. You are on the list.",
            )
            appendLine()
            number?.let {
                appendLine("Your number for this event is $it.")
                appendLine(
                    "If the photographer needs to tell you apart from somebody with the same " +
                        "name, this is what they will ask for.",
                )
                appendLine()
            }
            appendLine(
                "Nothing else is needed from you. Your photographs will arrive by email once " +
                    "the photographer has finished with them.",
            )
            appendLine()
            appendLine(
                "If you did not sign up for this, you can ignore this message and nothing " +
                    "further will be sent. Replying to this email reaches $studioName directly.",
            )
        }

    private fun confirmationHtml(
        eventName: String,
        studioName: String,
        givenName: String,
        number: Int?,
    ): String {
        val theirNumber =
            number?.let {
                """
                <p style="font-size:20px"><strong>Your number for this event is $it.</strong></p>
                <p>
                    If the photographer needs to tell you apart from somebody with the same
                    name, this is what they will ask for.
                </p>
                """.trimIndent()
            } ?: ""

        return """
            <p>Hello ${escapeHtml(givenName)},</p>
            <p>
                You scanned a code at ${escapeHtml(eventName)} and asked
                ${escapeHtml(studioName)} to send you your photographs. You are on the list.
            </p>
            $theirNumber
            <p>
                Nothing else is needed from you. Your photographs will arrive by email once the
                photographer has finished with them.
            </p>
            <p>
                If you did not sign up for this, you can ignore this message and nothing
                further will be sent. Replying to this email reaches ${escapeHtml(studioName)}
                directly.
            </p>
            <p>${escapeHtml(studioName)}</p>
            """.trimIndent()
    }

    /** A guest types their own name, and it goes into a page somebody else opens. */
    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

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
     *
     * The studio must still exist. Deleting an account is a mark now and a purge thirty days
     * later, and for those thirty days this went on handing out the studio's sign-up pages:
     * a stranger could scan a code belonging to a studio that had asked to be forgotten, give
     * an address, and be told photographs were coming. They were not, because the purge
     * destroys everything — so it collected personal data for an account being erased and
     * lied to the person who gave it. Found by running the walkthrough against production,
     * whose own last step deletes the studio it made.
     *
     * Joined here rather than checked by each caller, because both of them — reading the page
     * and signing up through it — are the same mistake, and one of them would have been
     * fixed and the other forgotten.
     */
    private fun resolve(token: String): Invite? =
        database.unscoped { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT i.studio_id, i.event_id
                    FROM event_invite i
                    JOIN studio s ON s.id = i.studio_id
                    WHERE i.token = ?
                      AND i.revoked_at IS NULL
                      AND s.deleted_at IS NULL
                    """.trimIndent(),
                ).use { statement ->
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
