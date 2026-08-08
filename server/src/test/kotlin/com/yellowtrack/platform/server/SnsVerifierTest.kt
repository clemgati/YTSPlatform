package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.mail.SnsMessage
import com.yellowtrack.platform.server.mail.SnsVerifier
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a posted SNS message really came from Amazon.
 *
 * This is the only thing standing between a public URL and the deployment's record of
 * whether mail works, so it is tested by actually signing messages with a real key and
 * actually verifying them — not by asserting that a boolean was passed through.
 *
 * The key and certificate below are a throwaway pair generated for this test. Nothing signs
 * anything real with them and they are worth exactly nothing if they leak.
 */
class SnsVerifierTest {
    /** The happy path, with a genuine RSA signature over the canonical string. */
    @Test
    fun `accepts a message signed by the certificate it names`() {
        val message = signed(notification())

        assertTrue(verifier().isAuthentic(message), "a correctly signed message must verify")
    }

    /**
     * The property that matters. If a changed field still verified, anybody could take one
     * genuine notification and rewrite it into any other.
     */
    @Test
    fun `refuses a message whose body was changed after signing`() {
        val original = signed(notification())
        val tampered = original.copy(message = original.message.replace("Delivery", "Bounce"))

        assertFalse(verifier().isAuthentic(tampered), "a rewritten payload must not verify")
    }

    /** The signature covers the id too, so a replayed signature cannot be re-pointed. */
    @Test
    fun `refuses a message whose id was changed after signing`() {
        val original = signed(notification())
        val tampered = original.copy(messageId = "some-other-id")

        assertFalse(verifier().isAuthentic(tampered))
    }

    /**
     * The check that stops the sender supplying both halves of the proof — and the one that
     * stops this being a server-side request forgery.
     *
     * Asserts the fetch was never *attempted*, not merely that the result was false. A
     * verifier that fetched an attacker's URL and then rejected the answer would pass a
     * weaker test while still making the request from inside the instance.
     */
    @Test
    fun `never fetches a certificate from a host that is not Amazon`() {
        var attempted: String? = null
        val verifier =
            SnsVerifier { url ->
                attempted = url
                CERTIFICATE
            }

        val message = signed(notification()).copy(signingCertUrl = "https://sns.example.com/evil.pem")

        assertFalse(verifier.isAuthentic(message), "a certificate from anywhere else proves nothing")
        assertEquals(null, attempted, "the URL must be rejected before anything is fetched")
    }

    /** `endsWith("amazonaws.com")` without the dot accepts this, and somebody can register it. */
    @Test
    fun `refuses a lookalike domain`() {
        assertFalse(SnsVerifier.isAmazonCertificateUrl("https://sns.notamazonaws.com/cert.pem"))
    }

    /** A certificate fetched over plain HTTP can be replaced in flight by anyone on the path. */
    @Test
    fun `refuses a certificate url that is not https`() {
        assertFalse(SnsVerifier.isAmazonCertificateUrl("http://sns.eu-west-1.amazonaws.com/cert.pem"))
    }

    @Test
    fun `accepts the real shape of an Amazon certificate url`() {
        assertTrue(
            SnsVerifier.isAmazonCertificateUrl(
                "https://sns.eu-west-1.amazonaws.com/SimpleNotificationService-abc123.pem",
            ),
        )
    }

    /**
     * Version 2 is SHA-256. Supported because AWS introduced it and a deployment can be
     * switched to it without anything here changing.
     */
    @Test
    fun `accepts signature version 2`() {
        val message = signed(notification(), version = "2", algorithm = "SHA256withRSA")

        assertTrue(verifier().isAuthentic(message))
    }

    /**
     * An unknown version is refused rather than guessed at. Guessing would mean trying
     * algorithms until one verified, which is a way of accepting something unintended.
     */
    @Test
    fun `refuses an unknown signature version`() {
        val message = signed(notification()).copy(signatureVersion = "9")

        assertFalse(verifier().isAuthentic(message))
    }

    @Test
    fun `refuses a message carrying no signature at all`() {
        val message = notification().copy(signature = null)

        assertFalse(verifier().isAuthentic(message))
    }

    /**
     * A subscription confirmation signs a different set of fields from a notification —
     * `SubscribeURL` and `Token` instead of `Subject`. Getting that wrong would mean the
     * subscription could never be confirmed and no bounce would ever arrive.
     */
    @Test
    fun `verifies a subscription confirmation which signs different fields`() {
        val confirmation =
            SnsMessage(
                type = SnsMessage.SUBSCRIPTION_CONFIRMATION,
                messageId = "confirm-1",
                topicArn = TOPIC,
                message = "You have chosen to subscribe",
                timestamp = "2026-08-08T15:30:28.000Z",
                subscribeUrl = "https://sns.eu-west-1.amazonaws.com/?Action=ConfirmSubscription",
                token = "a-token",
            )

        assertTrue(verifier().isAuthentic(signed(confirmation)))
    }

    /** The canonical string is alphabetical by field name and not JSON order. */
    @Test
    fun `builds the canonical string in the order Amazon signs`() {
        val canonical = notification().canonicalString()

        assertEquals(
            listOf("Message", "MessageId", "Subject", "Timestamp", "TopicArn", "Type"),
            canonical.lines().filterIndexed { index, _ -> index % 2 == 0 }.filter { it.isNotEmpty() },
        )
    }

    /** An absent optional field contributes nothing rather than an empty value. */
    @Test
    fun `omits an absent subject entirely`() {
        val canonical = notification().copy(subject = null).canonicalString()

        assertFalse(canonical.contains("Subject"), "an absent field must not appear at all")
    }

    // -- Fixtures --------------------------------------------------------------------------

    private fun verifier() = SnsVerifier { CERTIFICATE }

    private fun notification() =
        SnsMessage(
            type = SnsMessage.NOTIFICATION,
            messageId = "message-1",
            topicArn = TOPIC,
            subject = "Amazon SES Email Event Notification",
            message = """{"notificationType":"Delivery"}""",
            timestamp = "2026-08-08T15:30:28.000Z",
        )

    private fun signed(
        message: SnsMessage,
        version: String = "1",
        algorithm: String = "SHA1withRSA",
    ): SnsMessage {
        val key =
            KeyFactory
                .getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(PRIVATE_KEY)))

        val signature =
            Signature.getInstance(algorithm).run {
                initSign(key)
                update(message.canonicalString().toByteArray(Charsets.UTF_8))
                Base64.getEncoder().encodeToString(sign())
            }

        return message.copy(
            signatureVersion = version,
            signature = signature,
            signingCertUrl = "https://sns.eu-west-1.amazonaws.com/SimpleNotificationService-test.pem",
        )
    }

    private companion object {
        const val TOPIC = "arn:aws:sns:eu-west-1:123456789012:yellowtrack-ses"

        val CERTIFICATE =
            """
            -----BEGIN CERTIFICATE-----
            MIIDJTCCAg2gAwIBAgIUOxwsD1NDdEW2AYgSf4FCxIt+r9EwDQYJKoZIhvcNAQEL
            BQAwITEfMB0GA1UEAwwWc25zLnRlc3QuYW1hem9uYXdzLmNvbTAgFw0yNjA4MDgx
            NTQ2MzBaGA8yMTI2MDcxNTE1NDYzMFowITEfMB0GA1UEAwwWc25zLnRlc3QuYW1h
            em9uYXdzLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANFag5ic
            c8RHYd5tIm2oTRu/34qvf5jidtsU/ddCh7RfMohuhyvl2t+Jayn+uEX82K1LtjvO
            gBR2XcTPF/hQtN+EpfUTddcGLE/L7VSxZ+DBziPo5+r4uIwIt71CSrXq6Hb/nNXm
            w/7Zz9WkURSbFYadal097hj9ALxu8SJE74HmvL4WA7GxR8NiCty/J7y8AqYmyRfI
            H8YV5NVzS3VmIfjnlKsNjuQaVyl3HaC1DBaMj+ncD6Wtl9t4zC/6dNFqM2RSAo9N
            zWdDK/kIReaergICjouLNVG+gnipCvzdu19Dw7jUc79J35iH1ouhp6Br/7CSO8nl
            u8rPWrcaVl8AvJsCAwEAAaNTMFEwHQYDVR0OBBYEFL+Uy+HHhYBFy+az3z1VDeyA
            2zJgMB8GA1UdIwQYMBaAFL+Uy+HHhYBFy+az3z1VDeyA2zJgMA8GA1UdEwEB/wQF
            MAMBAf8wDQYJKoZIhvcNAQELBQADggEBAE3nqv0gypUdEzKtBgD+vHi36AEXGoy9
            cWd8j/MnskVLckhRqPEbx4ZtaGf0Nk8GJRp9tjm0+nAqIlxEAbAMe/uZx1yF/O1y
            BdOgi8p7/AwK2LRLuGvSkkWxxKhoqoOrKd02gZXNZZAkEuO6k3JSXqC8Tr2urOWx
            NiBFaZ/bmx69awXhwjaItCZPj4wzQ9EAMo/S3sSHVOrbpgy7nQNb5yafn8d1ClZp
            vRqzWG6r/hWqfKyCLSl/chZRPbkfgSJ5Jn+zPjkLqhbHxbJ5OkrIJHNSTLItdEco
            Ds9YrAK/ztVwSbqJ1JyawS/dUD2KYCBgDg8Q5CFpqIhc4ddT8Zvg7nA=
            -----END CERTIFICATE-----
            """.trimIndent()

        const val PRIVATE_KEY =
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDRWoOYnHPER2He" +
                "bSJtqE0bv9+Kr3+Y4nbbFP3XQoe0XzKIbocr5drfiWsp/rhF/NitS7Y7zoAUdl3E" +
                "zxf4ULTfhKX1E3XXBixPy+1UsWfgwc4j6Ofq+LiMCLe9Qkq16uh2/5zV5sP+2c/V" +
                "pFEUmxWGnWpdPe4Y/QC8bvEiRO+B5ry+FgOxsUfDYgrcvye8vAKmJskXyB/GFeTV" +
                "c0t1ZiH455SrDY7kGlcpdx2gtQwWjI/p3A+lrZfbeMwv+nTRajNkUgKPTc1nQyv5" +
                "CEXmnq4CAo6LizVRvoJ4qQr83btfQ8O41HO/Sd+Yh9aLoaega/+wkjvJ5bvKz1q3" +
                "GlZfALybAgMBAAECggEANT3at8YaHvG0bxe8KL0jlwoN9Lw7LAt0BLVq9QKjdni7" +
                "Zj0NvVlAd5cAHPb9sDbkd/YIS19x7UJJCJNOWkVUKoAWoKQpVNzqCgUnv2E6tMs0" +
                "/KbvzC8i5+ITsFsamvc51YGeRjvg3oBQPdoDEMV8DPcdpMbqNYqqVEG2q2tyqcsp" +
                "ZFUguMA7WqXItAC6AfLmM5UTpUqqyTHjZ2Ng3ffXbiCtSjsedMii65uoAQGNiPwC" +
                "BjCuYuayIEzIFkM4TUmrJZr52UY0RRw8t8zrkOVzEpjvsvlE1IW8BOZLSLhSCUjO" +
                "O/sAOpBV7FVwYu5iBXkAciK722MW47ViEcbq4uQYyQKBgQD5PsMYsPoRT9fSa+sG" +
                "mH52vcSP+5yesIS2cmPxseoTWFpIQuvPQdlmMm0dcjXur8ycbc4D0z7QQQXS9IID" +
                "cZf366BI5Lv1zASkCVbUGWECyl6TgD1HJY6mjHBTxy51nOBVT+xfXoJliZWGTnfJ" +
                "39trbWij36t12+EdCjDfDC8rNQKBgQDXBvzy7iWCUJBUB69Xw/99tT867qOVyo4t" +
                "RtznzgKtwQk6uqslVPJpZYcgcTH0bca1kQ5cIP8NiTt5Gd/GMrXTWAmjHUpZj1kb" +
                "HL+pd9NNrRSjselXvjPvuh0I4IGBHYEo4Tcl6P7jF9Gh3rhnp1vxVwkAETIso5ZP" +
                "YfjkdQJyjwKBgQCMG2PANXvcz20as99hzccJKJOd/GqGKePwS00zSDe0uC3IARZd" +
                "Yz653A7KRrFagygZpv/v1oewuVx0wU10ch5jFuj7ENk3yn+xVi6g+8M0tl2DvMq1" +
                "e788kCV0crsDEjRowIy274GNYlHTNbjQmi3VraLsu4a+seNRV7ExMMAzIQKBgHFL" +
                "4k2Rwzu1fUZ5Qh8pS14N+MHxaUoMjvs2QkD1IB4y/szt/C1QY+W6tAcY/Ww/xxp1" +
                "0q4iSKD2NNrrEigZIgq4cWN7lGg6CoYpkKcXVsOvtZdGr58mvbDLTG8X88R3Kk3C" +
                "1M7pxBsdurviYSFkYiJ4bGqXpOs2SoWLJpwhNufrAoGBAIgFPzJK75E0hzbeoVwq" +
                "3QITl3Z7Gj/uwIzsfNv+N2BLuX1SvXHtHfOTdO+nhcz5p2NfKUsjXhP/KKDTHyXm" +
                "w4pQg8YD1O7WAzg596zrsvGng8qn0eFSHMiib8khCmmTdz21JX/IsDjANKi5rcuc" +
                "RMF87eVfE7KmnEmD0DlSfL6z"
    }
}
