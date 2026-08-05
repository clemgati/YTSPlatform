package com.yellowtrack.platform.server.document

import com.yellowtrack.platform.server.Database
import com.yellowtrack.platform.server.mail.Email
import com.yellowtrack.platform.server.mail.Mailer
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** Why a document could not be sent, in words the studio should read. */
sealed class SendRefused(
    message: String,
) : Exception(message) {
    data object NoStudioEmail : SendRefused(
        "Add your studio's email address in Settings first — it is where your client's reply will go.",
    )

    data object NotConfigured : SendRefused("This server cannot send mail. Nothing was sent.")

    data class TooMany(
        val limit: Int,
    ) : SendRefused("That is $limit documents today, which is all this studio can send. Try again tomorrow.")

    data object Failed : SendRefused("That could not be sent. Nothing has reached your client.")
}

/**
 * Sends a rendered document to a studio's client, as the studio.
 *
 * The act this performs is not the one [com.yellowtrack.platform.server.auth.PasswordResets]
 * performs, and ADR 0011 exists because the difference is not obvious. A reset goes from
 * Yellow Track to its own account holder. This goes to somebody who has never heard of Yellow
 * Track, about money, on behalf of a business whose domain this deployment does not control.
 *
 * So the sending address stays this deployment's — SES signs for the verified domain, and a
 * message signed by one domain while claiming to be from another is a message in a spam
 * folder — and the studio's identity is carried in the display name and `Reply-To` instead.
 */
class DocumentMail(
    private val database: Database,
    private val mailer: Mailer?,
    private val fromAddress: String?,
    private val dailyLimit: Int = DEFAULT_DAILY_LIMIT,
    private val now: () -> Long = System::currentTimeMillis,
    private val onSendFailure: (Throwable) -> Unit = { it.printStackTrace() },
) {
    private val sent = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Sends, or throws [SendRefused].
     *
     * Failures are surfaced rather than swallowed, which is the opposite of ADR 0010 decision
     * 6 and deliberate: that rule protects the account-existence answer, and here the studio
     * typed the address and already knows its own client exists.
     */
    fun send(
        studioId: String,
        to: String,
        subject: String,
        html: String,
        text: String,
    ) {
        val mailer = mailer ?: throw SendRefused.NotConfigured
        val from = fromAddress ?: throw SendRefused.NotConfigured

        val studio = studioOf(studioId)

        // Not optional. Without it a client's reply goes to whoever owns the sending address,
        // and the photographer waiting to be paid never learns there was a question.
        val replyTo = studio?.email?.takeIf { it.isNotBlank() } ?: throw SendRefused.NoStudioEmail

        recordOrRefuse(studioId)

        val email =
            Email(
                to = to,
                subject = subject,
                body = text,
                html = html,
                // What a client reads in its inbox list. Mail clients render this as
                // "Harbourline Photography via yellowtrackstudios.com", which is honest: a
                // third party did send it.
                fromName = studio.name,
                fromAddress = from,
                replyTo = replyTo,
                // The application does not keep the rendered body, so this is the only copy
                // of what the client actually received.
                cc = replyTo,
            )

        runCatching { mailer.send(email) }
            .onFailure {
                onSendFailure(it)
                throw SendRefused.Failed
            }
    }

    /**
     * Counts this send against the studio's day, or refuses.
     *
     * Counted before the send rather than after. A limit applied to successes only is a limit
     * that a failing mail server turns off, and the reason for having one — that anybody who
     * can create a studio can send from this domain with a business name of their choosing —
     * does not care whether the messages arrived.
     *
     * In memory, so a restart forgives. That is the right trade at this size: the alternative
     * is a table and a migration to slow down an abuse nobody has attempted, and the ceiling
     * that actually matters is SES's own.
     */
    private fun recordOrRefuse(studioId: String) {
        val cutoff = now() - WINDOW.inWholeMilliseconds

        val timestamps = sent.computeIfAbsent(studioId) { mutableListOf() }

        synchronized(timestamps) {
            timestamps.removeAll { it <= cutoff }
            if (timestamps.size >= dailyLimit) throw SendRefused.TooMany(dailyLimit)
            timestamps.add(now())
        }
    }

    private fun studioOf(studioId: String): StudioIdentity? =
        database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    "SELECT name, email FROM studio_profile WHERE studio_id = ? AND deleted_at IS NULL",
                ).use { statement ->
                    statement.setString(1, studioId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) StudioIdentity(rows.getString(1), rows.getString(2)) else null
                    }
                }
        }

    private data class StudioIdentity(
        val name: String,
        val email: String?,
    )

    companion object {
        /**
         * Enough for a working day of invoices, and far short of anything worth abusing.
         *
         * Sign-up is open, so this is what stands between the domain and an open relay with a
         * nice interface — ADR 0011 decision 9, which makes it part of shipping rather than a
         * follow-up.
         */
        const val DEFAULT_DAILY_LIMIT = 50

        private val WINDOW: Duration = 1.days

        fun fromEnvironment(): String? = System.getenv("DOCUMENT_FROM")
    }
}
