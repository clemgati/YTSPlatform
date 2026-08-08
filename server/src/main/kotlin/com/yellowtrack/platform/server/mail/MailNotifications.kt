package com.yellowtrack.platform.server.mail

import com.yellowtrack.platform.server.Database
import java.time.Instant

/**
 * The durable half of the mail story.
 *
 * [MailHealth] holds what happened on the SMTP socket, in memory, on purpose — it describes
 * the running process. This holds what SES said afterwards, in the database, for the
 * opposite reason: a bounce is a fact about a message and an address, and forgetting it on
 * restart would mean the only durable record of mail failing is that somebody eventually
 * complains.
 *
 * The two answer different questions and both are worth having:
 *
 *  - `mailLastSucceededAt` — SMTP accepted a message. Proves the credentials work.
 *  - `lastDeliveredAt` here — SES confirmed it arrived. Proves somebody could have read it.
 *
 * Before this existed only the first could be said, and it was routinely mistaken for the
 * second.
 */
class MailNotifications(
    private val database: Database,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Writes what a notification says, and does nothing if it has been written already.
     *
     * Returns how many rows were new, which is what the route logs — a redelivery quietly
     * writing nothing is correct, and a redelivery silently looking identical to a fresh
     * bounce is how a count stops meaning anything.
     */
    fun record(
        messageId: String,
        notification: SesNotification,
    ): Int {
        val recordedAt = now()
        val rows = rowsFor(messageId, notification, recordedAt)
        if (rows.isEmpty()) return 0

        return database.unscoped { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO mail_notification
                        (id, recipient, kind, subtype, detail, occurred_at, recorded_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id, recipient) DO NOTHING
                    """.trimIndent(),
                ).use { statement ->
                    rows.forEach { row ->
                        statement.setString(1, row.id)
                        statement.setString(2, row.recipient)
                        statement.setString(3, row.kind)
                        statement.setString(4, row.subtype)
                        statement.setString(5, row.detail)
                        statement.setLong(6, row.occurredAt)
                        statement.setLong(7, row.recordedAt)
                        statement.addBatch()
                    }
                    statement.executeBatch().count { it > 0 }
                }
        }
    }

    /** What `/ready` reports, in one query per question. */
    fun summary(window: Long = SEVEN_DAYS): MailDeliverySummary {
        val since = now() - window
        return database.unscoped { connection ->
            val lastDelivered =
                connection
                    .prepareStatement(
                        "SELECT max(occurred_at) FROM mail_notification WHERE kind = ?",
                    ).use { statement ->
                        statement.setString(1, DELIVERY)
                        statement.executeQuery().use { rows ->
                            if (rows.next()) rows.getLong(1).takeIf { !rows.wasNull() } else null
                        }
                    }

            val counts =
                connection
                    .prepareStatement(
                        """
                        SELECT kind, count(*) FROM mail_notification
                        WHERE kind IN (?, ?) AND occurred_at >= ?
                        GROUP BY kind
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, BOUNCE)
                        statement.setString(2, COMPLAINT)
                        statement.setLong(3, since)
                        statement.executeQuery().use { rows ->
                            buildMap {
                                while (rows.next()) put(rows.getString(1), rows.getInt(2))
                            }
                        }
                    }

            MailDeliverySummary(
                lastDeliveredAt = lastDelivered,
                recentBounces = counts[BOUNCE] ?: 0,
                recentComplaints = counts[COMPLAINT] ?: 0,
            )
        }
    }

    private fun rowsFor(
        messageId: String,
        notification: SesNotification,
        recordedAt: Long,
    ): List<Row> =
        when (notification.notificationType) {
            BOUNCE -> {
                val bounce = notification.bounce
                val occurredAt = epochMillis(bounce?.timestamp, recordedAt)
                bounce?.bouncedRecipients.orEmpty().map { recipient ->
                    Row(
                        id = messageId,
                        recipient = recipient.emailAddress,
                        kind = BOUNCE,
                        subtype = bounce?.bounceType,
                        // The remote server's diagnostic first, because it is the only text
                        // that ever says *why*; the SES sub-type is a category.
                        detail = (recipient.diagnosticCode ?: bounce?.bounceSubType)?.take(MAX_DETAIL),
                        occurredAt = occurredAt,
                        recordedAt = recordedAt,
                    )
                }
            }

            COMPLAINT -> {
                val complaint = notification.complaint
                val occurredAt = epochMillis(complaint?.timestamp, recordedAt)
                complaint?.complainedRecipients.orEmpty().map { recipient ->
                    Row(
                        id = messageId,
                        recipient = recipient.emailAddress,
                        kind = COMPLAINT,
                        subtype = complaint?.complaintFeedbackType,
                        detail = null,
                        occurredAt = occurredAt,
                        recordedAt = recordedAt,
                    )
                }
            }

            DELIVERY -> {
                val delivery = notification.delivery
                val occurredAt = epochMillis(delivery?.timestamp, recordedAt)
                delivery?.recipients.orEmpty().map { address ->
                    Row(
                        id = messageId,
                        recipient = address,
                        kind = DELIVERY,
                        subtype = null,
                        detail = null,
                        occurredAt = occurredAt,
                        recordedAt = recordedAt,
                    )
                }
            }

            // A type this does not know about is not an error. SES has added notification
            // types before and a subscription can be configured to send them; ignoring one
            // is better than 500ing at Amazon, which would make SNS retry it forever.
            else -> emptyList()
        }

    /**
     * SES timestamps are ISO-8601. An unparseable one falls back to the time of receipt
     * rather than being dropped: a bounce with a slightly wrong time is still a bounce, and
     * losing it to a format change would be losing the thing this table exists for.
     */
    private fun epochMillis(
        timestamp: String?,
        fallback: Long,
    ): Long = timestamp?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: fallback

    private data class Row(
        val id: String,
        val recipient: String,
        val kind: String,
        val subtype: String?,
        val detail: String?,
        val occurredAt: Long,
        val recordedAt: Long,
    )

    companion object {
        const val BOUNCE = "Bounce"
        const val COMPLAINT = "Complaint"
        const val DELIVERY = "Delivery"

        private const val MAX_DETAIL = 500
        private const val SEVEN_DAYS = 7L * 24 * 60 * 60 * 1000
    }
}

/** What SES has said lately, as `/ready` reports it. */
data class MailDeliverySummary(
    val lastDeliveredAt: Long?,
    val recentBounces: Int,
    val recentComplaints: Int,
)
