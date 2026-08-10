package com.yellowtrack.platform.server.event

import com.yellowtrack.platform.server.Database
import com.yellowtrack.platform.server.mail.Email
import com.yellowtrack.platform.server.mail.Mailer
import java.sql.Connection

/** Why a sitting could not be handed over. */
sealed class DeliveryRefused(
    message: String,
) : Exception(message) {
    data object NoSuchSitting : DeliveryRefused("That sitting is not there.")

    /**
     * ADR 0013: photographs are held until the slot is closed.
     *
     * A slot still open is one the photographer is shooting into, so its contents are not yet
     * a set of photographs of one person — they are a set of photographs of whoever has been
     * in front of the camera since it opened.
     */
    data object StillOpen : DeliveryRefused("That sitting is still open. Close it first.")

    data object NothingToSend : DeliveryRefused("There are no photographs in that sitting yet.")

    data object NotConfigured : DeliveryRefused("This server cannot send mail. Nothing was sent.")

    data object NoStudioEmail : DeliveryRefused(
        "Add your studio's email address in Settings first — it is where a reply will go.",
    )

    data object Failed : DeliveryRefused("That could not be sent. Nothing has reached them.")
}

/** What a delivery did, for the studio's screen. */
data class Delivered(
    val email: String,
    val photographs: Int,
    /** False when this sitting had already been handed over and nothing was sent again. */
    val sentNow: Boolean,
)

/**
 * Handing a sitting to the person in it.
 *
 * ## Why this is an act rather than a consequence
 *
 * Closing a slot does not deliver it. A slot is advanced by a photographer tapping a name on
 * a laptop between two strangers sitting down, and the failure that follows a mistap is one
 * person receiving another person's photographs. ADR 0013 holds the photographs until
 * somebody has looked, and this is what somebody having looked amounts to.
 *
 * ## The order, which is the reverse of the one in `StoredObjects`
 *
 * There, the row went before the bytes: an object in a bucket with no row is unfindable, and
 * a row with no object is harmless. Here the mark goes *after* the send, and for the mirror
 * of that reason — the irreversible half is the email.
 *
 * A delivery marked and not sent is invisible: the studio sees "delivered", nothing retries,
 * and the guest goes home with nothing. A delivery sent and not marked is a second email to
 * somebody who already has their photographs, which is a nuisance they can read and ignore.
 * So the mark comes last, and the failure mode is the one that announces itself.
 */
class EventDelivery(
    private val database: Database,
    private val mailer: Mailer?,
    private val fromAddress: String?,
    private val photosUrl: String = System.getenv("PHOTOS_URL")?.trimEnd('/') ?: "https://yellowtrackphotos.com",
    private val now: () -> Long = System::currentTimeMillis,
    private val newToken: () -> String = EventInvites::randomToken,
    private val onSendFailure: (Throwable) -> Unit = { it.printStackTrace() },
) {
    /**
     * Sends somebody the photographs of their sitting.
     *
     * Idempotent by intent rather than by accident: a sitting already delivered returns
     * `sentNow = false` and sends nothing. A studio tapping twice does not mail twice.
     */
    fun deliver(
        studioId: String,
        slotId: String,
    ): Delivered {
        val mailer = mailer ?: throw DeliveryRefused.NotConfigured
        val from = fromAddress ?: throw DeliveryRefused.NotConfigured

        val sitting =
            database.inStudio(studioId) { connection -> sitting(connection, slotId) }
                ?: throw DeliveryRefused.NoSuchSitting

        if (sitting.closedAt == null) throw DeliveryRefused.StillOpen
        if (sitting.photographs == 0) throw DeliveryRefused.NothingToSend
        if (sitting.deliveredAt != null) {
            return Delivered(email = sitting.email, photographs = sitting.photographs, sentNow = false)
        }

        // A studio that has never filled in Settings has no profile row at all, which is the
        // same position as one with a blank address: there is nowhere for a reply to go.
        val studio =
            database.inStudio(studioId) { connection -> studio(connection, studioId) }
                ?: throw DeliveryRefused.NoStudioEmail

        // Not optional, for the reason ADR 0011 gives about documents: without a reply
        // address, somebody replying "these are not me" reaches whoever owns the sending
        // domain rather than the photographer who can fix it.
        val replyTo = studio.email?.takeIf { it.isNotBlank() } ?: throw DeliveryRefused.NoStudioEmail

        // Before the send, because the link has to be in the message. Issuing a token for
        // somebody who then receives nothing costs nothing — an unused row nobody knows.
        val token =
            database.inStudio(studioId) { connection ->
                galleryToken(connection, studioId, sitting.registrationId)
            }

        val link = "$photosUrl/gallery/$token"

        runCatching {
            mailer.send(
                Email(
                    to = sitting.email,
                    subject = "Your photographs from ${sitting.eventName}",
                    body = plainText(sitting, link),
                    html = html(sitting, studio.name, link),
                    // The studio's name, this deployment's address — SES signs for the domain
                    // it can sign for, and a message signed by one domain while claiming
                    // another is a message in a spam folder.
                    fromName = studio.name,
                    fromAddress = from,
                    replyTo = replyTo,
                ),
            )
        }.onFailure {
            onSendFailure(it)
            // Deliberately not marked. The sitting stays undelivered so it can be sent again,
            // which is the whole reason the mark comes after the send.
            throw DeliveryRefused.Failed
        }

        database.inStudio(studioId) { connection ->
            connection
                .prepareStatement("UPDATE event_slot SET delivered_at = ? WHERE id = ? AND delivered_at IS NULL")
                .use { statement ->
                    statement.setLong(1, now())
                    statement.setString(2, slotId)
                    statement.executeUpdate()
                }
        }

        return Delivered(email = sitting.email, photographs = sitting.photographs, sentNow = true)
    }

    // -- Internals -----------------------------------------------------------------------

    private data class Sitting(
        val registrationId: String,
        val email: String,
        val name: String?,
        val eventName: String,
        val closedAt: Long?,
        val deliveredAt: Long?,
        val photographs: Int,
    )

    private data class StudioDetails(
        val name: String,
        val email: String?,
    )

    private fun sitting(
        connection: Connection,
        slotId: String,
    ): Sitting? =
        connection
            .prepareStatement(
                """
                SELECT r.id, r.email, r.name, e.name, s.closed_at, s.delivered_at,
                       (SELECT count(*) FROM event_photo p WHERE p.slot_id = s.id)
                FROM event_slot s
                JOIN event_registration r ON r.id = s.registration_id
                JOIN event_station t ON t.id = s.station_id
                JOIN event e ON e.id = t.event_id
                WHERE s.id = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, slotId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) return null

                    Sitting(
                        registrationId = rows.getString(1),
                        email = rows.getString(2),
                        name = rows.getString(3),
                        eventName = rows.getString(4),
                        closedAt = rows.getLong(5).takeUnless { rows.wasNull() },
                        deliveredAt = rows.getLong(6).takeUnless { rows.wasNull() },
                        photographs = rows.getInt(7),
                    )
                }
            }

    private fun studio(
        connection: Connection,
        studioId: String,
    ): StudioDetails? =
        connection
            .prepareStatement(
                "SELECT name, email FROM studio_profile WHERE studio_id = ? AND deleted_at IS NULL",
            ).use { statement ->
                statement.setString(1, studioId)
                statement.executeQuery().use { rows ->
                    if (rows.next()) StudioDetails(rows.getString(1), rows.getString(2)) else null
                }
            }

    /**
     * The registration's gallery token, issued once.
     *
     * Reused across sittings so somebody photographed twice at one event gets one link that
     * grows, rather than two links each showing half of what they have.
     *
     * Names the studio in both statements, because `event_gallery` carries no policy to do
     * it — the cost of that table being reachable without a session.
     */
    private fun galleryToken(
        connection: Connection,
        studioId: String,
        registrationId: String,
    ): String =
        connection
            .prepareStatement(
                """
                SELECT token FROM event_gallery
                WHERE registration_id = ? AND studio_id = ? AND revoked_at IS NULL
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, registrationId)
                statement.setString(2, studioId)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            } ?: newToken().also { token ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO event_gallery(token, studio_id, registration_id, created_at)
                    VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, token)
                    statement.setString(2, studioId)
                    statement.setString(3, registrationId)
                    statement.setLong(4, now())
                    statement.executeUpdate()
                }
        }

    private fun plainText(
        sitting: Sitting,
        link: String,
    ): String =
        buildString {
            appendLine(sitting.name?.let { "Hello $it," } ?: "Hello,")
            appendLine()
            appendLine("Your photographs from ${sitting.eventName} are ready.")
            appendLine()
            appendLine(link)
            appendLine()
            // Said plainly, because somebody who was photographed at a conference has no
            // relationship with this company and no reason to guess what the link is for.
            appendLine("The link is yours alone — anybody you send it to can see the photographs too.")
        }

    private fun html(
        sitting: Sitting,
        studioName: String,
        link: String,
    ): String {
        val greeting = sitting.name?.let { "Hello ${escape(it)}," } ?: "Hello,"

        return """
            <p>$greeting</p>
            <p>Your photographs from ${escape(sitting.eventName)} are ready.</p>
            <p><a href="${escape(link)}">See your photographs</a></p>
            <p>The link is yours alone — anybody you send it to can see the photographs too.</p>
            <p>${escape(studioName)}</p>
            """.trimIndent()
    }

    /**
     * An event name and a person's name both come from somebody typing, and both land in
     * HTML that goes to a stranger's inbox.
     */
    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
