package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.mail.MailNotifications
import com.yellowtrack.platform.server.mail.SesBounce
import com.yellowtrack.platform.server.mail.SesNotification
import com.yellowtrack.platform.server.mail.SesRecipient
import com.yellowtrack.platform.server.mail.SnsMessage
import com.yellowtrack.platform.server.mail.sesNotificationRoutes
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the deployment does when Amazon tells it a message bounced.
 *
 * Against the real Postgres, because the two properties worth having — that a redelivery
 * does not inflate a count, and that `/ready` can answer from what was recorded — are both
 * properties of the table rather than of the route.
 *
 * The signature itself is faked here and tested for real in [SnsVerifierTest]. What this
 * covers is everything around it: who is refused, what is written, and what a retry does.
 */
class SesNotificationTest {
    /**
     * The check that a valid Amazon signature alone does not pass.
     *
     * Any AWS customer can create a topic and sign perfectly. Without this, publishing
     * invented bounces at this URL is a thing a stranger can do.
     */
    @Test
    fun `refuses a correctly signed message from somebody else's topic`() =
        withRoute { client ->
            val response =
                client.post("/ses/notifications") {
                    setBody(envelope(topicArn = "arn:aws:sns:eu-west-1:999999999999:not-ours"))
                }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    @Test
    fun `refuses a message whose signature does not verify`() =
        withRoute(authentic = false) { client ->
            val response = client.post("/ses/notifications") { setBody(envelope()) }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    /**
     * Unconfigured is closed, not open. A deployment that has not set `SES_TOPIC_ARN` has no
     * way to tell our notifications from anyone's, so the route is simply not in service.
     */
    @Test
    fun `refuses everything when no topic is configured`() =
        withRoute(topicArn = null) { client ->
            val response = client.post("/ses/notifications") { setBody(envelope()) }

            assertEquals(HttpStatusCode.Forbidden, response.status)
        }

    /** Nothing arrives until the subscription is accepted, so this fetch is the whole feature. */
    @Test
    fun `confirms a subscription by fetching the url Amazon supplied`() {
        var fetched: String? = null
        withRoute(confirm = { url ->
            fetched = url
            true
        }) { client ->
            val body =
                """
                {"Type":"SubscriptionConfirmation","MessageId":"${UUID.randomUUID()}",
                 "TopicArn":"$TOPIC","Message":"You have chosen to subscribe",
                 "Timestamp":"2026-08-08T15:30:28.000Z",
                 "SubscribeURL":"https://sns.eu-west-1.amazonaws.com/?Action=ConfirmSubscription",
                 "Token":"a-token"}
                """.trimIndent()

            val response = client.post("/ses/notifications") { setBody(body) }

            assertEquals(HttpStatusCode.OK, response.status)
        }
        assertEquals(
            "https://sns.eu-west-1.amazonaws.com/?Action=ConfirmSubscription",
            fetched,
            "the subscription is only live once this URL has been fetched",
        )
    }

    /** A bounce becomes a row naming the address and why, which is the point of all of this. */
    @Test
    fun `records a bounce against the address that bounced`() {
        val address = "bounced-${UUID.randomUUID()}@example.com"
        val notifications = MailNotifications(TestDatabase.database)

        withRoute(notifications = notifications) { client ->
            val response =
                client.post("/ses/notifications") {
                    setBody(envelope(message = bouncePayload(address)))
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

        val row = assertNotNull(rowFor(address), "the bounce should have been recorded")
        assertEquals("Bounce", row.kind)
        assertEquals("Permanent", row.subtype, "permanent and transient are not the same problem")
        assertTrue(
            row.detail!!.contains("550"),
            "the remote server's diagnostic is the only text that says why",
        )
    }

    /**
     * SNS retries on anything that is not 2xx and can redeliver even after a 200. Without
     * idempotence one bounce becomes a rising count, and a rising count is what somebody
     * would act on.
     */
    @Test
    fun `records a redelivered notification only once`() {
        val address = "repeat-${UUID.randomUUID()}@example.com"
        val messageId = UUID.randomUUID().toString()
        val notifications = MailNotifications(TestDatabase.database)

        withRoute(notifications = notifications) { client ->
            repeat(3) {
                client.post("/ses/notifications") {
                    setBody(envelope(messageId = messageId, message = bouncePayload(address)))
                }
            }
        }

        assertEquals(1, countFor(address), "three deliveries of one notification is still one bounce")
    }

    /**
     * The same property at the store, where it can actually be seen to hold.
     *
     * The route test above passes with idempotence removed — the route logs the duplicate-key
     * failure and moves on, so the count stays at one either way. That makes it a test of the
     * route's tolerance rather than of the table's key, which is not what it looks like.
     * Asserting on what `record` *wrote* is what distinguishes "written once" from "refused
     * the second time", and it fails without the `ON CONFLICT`.
     */
    @Test
    fun `writes a redelivered notification once and reports that it wrote nothing`() {
        val address = "second-${UUID.randomUUID()}@example.com"
        val messageId = UUID.randomUUID().toString()
        val notifications = MailNotifications(TestDatabase.database)
        val payload =
            SesNotification(
                notificationType = "Bounce",
                bounce =
                    SesBounce(
                        bounceType = "Permanent",
                        timestamp = "2026-08-08T15:30:28.000Z",
                        bouncedRecipients = listOf(SesRecipient(emailAddress = address)),
                    ),
            )

        assertEquals(1, notifications.record(messageId, payload), "the first is new")
        assertEquals(0, notifications.record(messageId, payload), "the second must write nothing")
        assertEquals(1, countFor(address))
    }

    /** The claim that could not be made before: somebody actually received it. */
    @Test
    fun `records a delivery so that arrival can be told from acceptance`() {
        val address = "delivered-${UUID.randomUUID()}@example.com"
        val notifications = MailNotifications(TestDatabase.database)

        withRoute(notifications = notifications) { client ->
            client.post("/ses/notifications") {
                setBody(envelope(message = deliveryPayload(address)))
            }
        }

        assertEquals("Delivery", assertNotNull(rowFor(address)).kind)
        assertNotNull(notifications.summary().lastDeliveredAt, "a delivery must reach the readiness summary")
    }

    /**
     * A notification type this does not know about answers 200 and writes nothing. A 500
     * would make SNS retry it for hours, crowding out the ones that matter.
     */
    @Test
    fun `ignores an unknown notification type without failing`() =
        withRoute { client ->
            val response =
                client.post("/ses/notifications") {
                    setBody(envelope(message = """{"notificationType":"SomethingNewFromAmazon"}"""))
                }

            assertEquals(HttpStatusCode.OK, response.status, "retrying this forever would help nobody")
        }

    /** Equally, a body that will never parse must not be retried for hours. */
    @Test
    fun `ignores a body it cannot read`() =
        withRoute { client ->
            val response = client.post("/ses/notifications") { setBody("not json at all") }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    /** Counts are windowed, so an old bounce stops being news. */
    @Test
    fun `counts only bounces inside the window`() {
        val address = "old-${UUID.randomUUID()}@example.com"
        val notifications = MailNotifications(TestDatabase.database)

        withRoute(notifications = notifications) { client ->
            client.post("/ses/notifications") {
                setBody(envelope(message = bouncePayload(address, timestamp = "2020-01-01T00:00:00.000Z")))
            }
        }

        val recent = notifications.summary().recentBounces
        val everything = notifications.summary(window = Long.MAX_VALUE / 2).recentBounces
        assertTrue(everything > recent, "a bounce from 2020 should not count as a bounce this week")
    }

    // -- Fixtures --------------------------------------------------------------------------

    private fun withRoute(
        topicArn: String? = TOPIC,
        authentic: Boolean = true,
        confirm: (String) -> Boolean = { true },
        notifications: MailNotifications? = MailNotifications(TestDatabase.database),
        block: suspend (HttpClient) -> Unit,
    ) = testApplication {
        application {
            routing {
                sesNotificationRoutes(
                    notifications = notifications,
                    expectedTopicArn = topicArn,
                    verify = { authentic },
                    confirm = confirm,
                )
            }
        }
        block(client)
    }

    private fun envelope(
        messageId: String = UUID.randomUUID().toString(),
        topicArn: String = TOPIC,
        message: String = """{"notificationType":"Delivery"}""",
    ): String {
        val escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
        return """
            {"Type":"${SnsMessage.NOTIFICATION}","MessageId":"$messageId","TopicArn":"$topicArn",
             "Message":"$escaped","Timestamp":"2026-08-08T15:30:28.000Z"}
            """.trimIndent()
    }

    private fun bouncePayload(
        address: String,
        timestamp: String = "2026-08-08T15:30:28.000Z",
    ) = """
        {"notificationType":"Bounce",
         "bounce":{"bounceType":"Permanent","bounceSubType":"General","timestamp":"$timestamp",
         "bouncedRecipients":[{"emailAddress":"$address",
         "diagnosticCode":"smtp; 550 5.1.1 user unknown"}]}}
        """.trimIndent()

    private fun deliveryPayload(
        address: String,
        timestamp: String = "2026-08-08T15:30:28.000Z",
    ) = """
        {"notificationType":"Delivery",
         "delivery":{"timestamp":"$timestamp","recipients":["$address"]}}
        """.trimIndent()

    private data class Recorded(
        val kind: String,
        val subtype: String?,
        val detail: String?,
    )

    private fun rowFor(address: String): Recorded? =
        TestDatabase.connection().use { connection ->
            connection
                .prepareStatement("SELECT kind, subtype, detail FROM mail_notification WHERE recipient = ?")
                .use { statement ->
                    statement.setString(1, address)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) Recorded(rows.getString(1), rows.getString(2), rows.getString(3)) else null
                    }
                }
        }

    private fun countFor(address: String): Int =
        TestDatabase.connection().use { connection ->
            connection
                .prepareStatement("SELECT count(*) FROM mail_notification WHERE recipient = ?")
                .use { statement ->
                    statement.setString(1, address)
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getInt(1)
                    }
                }
        }

    private companion object {
        const val TOPIC = "arn:aws:sns:eu-west-1:123456789012:yellowtrack-ses"
    }
}
