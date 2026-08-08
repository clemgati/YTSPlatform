package com.yellowtrack.platform.server.mail

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The envelope Amazon SNS posts, whatever is inside it.
 *
 * SNS wraps every notification in this and signs the wrapper, so the interesting payload —
 * what SES actually said — arrives as a JSON string inside [message] rather than as nested
 * objects. That is not a quirk to work around: the signature is over these fields, so
 * anything that re-parsed and re-serialised the inner document before checking it would be
 * verifying a different string from the one that was signed.
 */
@Serializable
data class SnsMessage(
    @SerialName("Type") val type: String,
    @SerialName("MessageId") val messageId: String,
    @SerialName("TopicArn") val topicArn: String? = null,
    @SerialName("Subject") val subject: String? = null,
    @SerialName("Message") val message: String,
    @SerialName("Timestamp") val timestamp: String,
    @SerialName("SignatureVersion") val signatureVersion: String? = null,
    @SerialName("Signature") val signature: String? = null,
    @SerialName("SigningCertURL") val signingCertUrl: String? = null,
    /** Present on a subscription confirmation, and the only way to accept the subscription. */
    @SerialName("SubscribeURL") val subscribeUrl: String? = null,
    @SerialName("Token") val token: String? = null,
) {
    /**
     * The exact bytes AWS signed.
     *
     * Field order is fixed by AWS and is alphabetical by name, not the order they appear in
     * the document — a detail worth stating because JSON key order looks like it ought to be
     * the answer and is not. Each present field contributes `name\nvalue\n`; absent optional
     * fields contribute nothing at all rather than an empty value.
     *
     * The two shapes differ, which is why this switches on [type] rather than building one
     * list: a confirmation signs `SubscribeURL` and `Token`, a notification signs `Subject`
     * when it has one.
     */
    fun canonicalString(): String {
        val fields =
            when (type) {
                SUBSCRIPTION_CONFIRMATION, UNSUBSCRIBE_CONFIRMATION ->
                    listOf(
                        "Message" to message,
                        "MessageId" to messageId,
                        "SubscribeURL" to subscribeUrl,
                        "Timestamp" to timestamp,
                        "Token" to token,
                        "TopicArn" to topicArn,
                        "Type" to type,
                    )

                else ->
                    listOf(
                        "Message" to message,
                        "MessageId" to messageId,
                        "Subject" to subject,
                        "Timestamp" to timestamp,
                        "TopicArn" to topicArn,
                        "Type" to type,
                    )
            }

        return buildString {
            fields.forEach { (name, value) ->
                if (value != null) {
                    append(name).append('\n').append(value).append('\n')
                }
            }
        }
    }

    companion object {
        const val NOTIFICATION = "Notification"
        const val SUBSCRIPTION_CONFIRMATION = "SubscriptionConfirmation"
        const val UNSUBSCRIBE_CONFIRMATION = "UnsubscribeConfirmation"
    }
}

/**
 * What SES puts inside [SnsMessage.message].
 *
 * Only the fields this reads are declared, and the parser is lenient about the rest —
 * SES adds fields over time and a strict reader would turn "Amazon shipped an improvement"
 * into "bounces stopped being recorded".
 */
@Serializable
data class SesNotification(
    val notificationType: String,
    val mail: SesMail? = null,
    val bounce: SesBounce? = null,
    val complaint: SesComplaint? = null,
    val delivery: SesDelivery? = null,
)

@Serializable
data class SesMail(
    val timestamp: String? = null,
    val destination: List<String> = emptyList(),
)

@Serializable
data class SesBounce(
    /** `Permanent` or `Transient`. A permanent bounce is an address that will never work. */
    val bounceType: String? = null,
    val bounceSubType: String? = null,
    val timestamp: String? = null,
    val bouncedRecipients: List<SesRecipient> = emptyList(),
)

@Serializable
data class SesComplaint(
    /** `abuse`, `fraud`, and so on. Null is allowed: the field is genuinely optional. */
    val complaintFeedbackType: String? = null,
    val timestamp: String? = null,
    val complainedRecipients: List<SesRecipient> = emptyList(),
)

@Serializable
data class SesDelivery(
    val timestamp: String? = null,
    val recipients: List<String> = emptyList(),
)

@Serializable
data class SesRecipient(
    val emailAddress: String,
    /** The remote server's own words. The most useful thing in the whole payload. */
    val diagnosticCode: String? = null,
    val status: String? = null,
)
