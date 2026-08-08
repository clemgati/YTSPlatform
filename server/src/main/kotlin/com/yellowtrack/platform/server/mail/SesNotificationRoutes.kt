package com.yellowtrack.platform.server.mail

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.yellowtrack.platform.server.mail.SesNotifications")

/**
 * Lenient on purpose. SES adds fields to its notifications and SNS adds fields to its
 * envelope; a strict reader would turn an Amazon improvement into an outage in the one part
 * of the system whose job is to notice outages.
 */
private val snsJson = Json { ignoreUnknownKeys = true }

/**
 * Where Amazon tells us what happened to the mail we sent.
 *
 * This is the only route in the server that is *unauthenticated and public by necessity*.
 * An SNS HTTPS subscription is a POST from Amazon's network with no bearer token, no shared
 * secret, and no key of ours — so the authentication is the signature on the message, and
 * the whole of [SnsVerifier] exists because of it.
 *
 * ## Why the topic is checked as well as the signature
 *
 * A valid Amazon signature proves an AWS customer sent it. It does not prove *we* are that
 * customer. Anyone with an account can create a topic, subscribe this URL, and publish
 * perfectly-signed notifications describing bounces that never happened — which would make a
 * healthy deployment look like it was failing, and is exactly the kind of thing somebody
 * does once for fun. Matching `TopicArn` against the configured one is what closes that,
 * and it is not optional: with `SES_TOPIC_ARN` unset this route refuses everything and says
 * so, rather than accepting anything Amazon-shaped.
 *
 * ## Why a failure still answers 200
 *
 * SNS retries anything that is not 2xx, for hours. A notification this cannot parse will
 * never become parseable, so retrying it is a way of being told the same thing repeatedly
 * while the real ones queue behind it. Refusals that mean something — a bad signature, the
 * wrong topic — answer 403, because those should be visible to whoever is pointing the wrong
 * thing at this URL.
 */
fun Route.sesNotificationRoutes(
    notifications: MailNotifications?,
    expectedTopicArn: String?,
    // A function rather than the class, so a test can state "this message is authentic"
    // without having to hold a private key. The real check is [SnsVerifier.isAuthentic] and
    // it has its own test.
    verify: (SnsMessage) -> Boolean = SnsVerifier()::isAuthentic,
    confirm: (String) -> Boolean = ::confirmSubscription,
) {
    route("/ses") {
        post("/notifications") {
            if (notifications == null || expectedTopicArn.isNullOrBlank()) {
                log.warn("an SNS notification arrived but SES_TOPIC_ARN is not set; refusing it")
                call.respond(HttpStatusCode.Forbidden, "not configured")
                return@post
            }

            val body = call.receiveText()
            val message =
                runCatching { snsJson.decodeFromString<SnsMessage>(body) }.getOrNull()
                    ?: run {
                        log.warn("could not parse an SNS message; ignoring it")
                        // 200: an unparseable body will not parse on the fourth attempt
                        // either.
                        call.respond(HttpStatusCode.OK, "ignored")
                        return@post
                    }

            if (!verify(message)) {
                log.warn("an SNS message failed signature verification; refusing it")
                call.respond(HttpStatusCode.Forbidden, "bad signature")
                return@post
            }

            if (message.topicArn != expectedTopicArn) {
                log.warn("an SNS message arrived from an unexpected topic: ${message.topicArn}")
                call.respond(HttpStatusCode.Forbidden, "unexpected topic")
                return@post
            }

            when (message.type) {
                SnsMessage.SUBSCRIPTION_CONFIRMATION -> {
                    val subscribeUrl = message.subscribeUrl
                    // Signed, from the right topic, and still fetched rather than trusted:
                    // confirming is a request made from inside the instance to a URL in the
                    // body, so the allowlist in `confirmSubscription` applies here too.
                    val confirmed = subscribeUrl != null && confirm(subscribeUrl)
                    if (confirmed) {
                        log.info("confirmed the SES notification subscription for ${message.topicArn}")
                    } else {
                        log.error("could not confirm the SES notification subscription; bounces will not arrive")
                    }
                    call.respond(HttpStatusCode.OK, if (confirmed) "confirmed" else "not confirmed")
                }

                SnsMessage.NOTIFICATION -> {
                    val notification =
                        runCatching { snsJson.decodeFromString<SesNotification>(message.message) }.getOrNull()
                    if (notification == null) {
                        log.warn("an SNS notification carried a payload this cannot read; ignoring it")
                        call.respond(HttpStatusCode.OK, "ignored")
                        return@post
                    }

                    val written =
                        runCatching { notifications.record(message.messageId, notification) }
                            .onFailure { log.error("could not record a mail notification", it) }
                            .getOrDefault(0)

                    // Logged at info for a delivery and warn for anything else, so the
                    // interesting ones are visible without reading every line.
                    if (notification.notificationType == MailNotifications.DELIVERY) {
                        log.info("SES delivered a message ($written recorded)")
                    } else if (written > 0) {
                        log.warn("SES reported ${notification.notificationType} for $written recipient(s)")
                    }

                    call.respond(HttpStatusCode.OK, "recorded")
                }

                else -> {
                    log.info("ignoring an SNS message of type ${message.type}")
                    call.respond(HttpStatusCode.OK, "ignored")
                }
            }
        }
    }
}
