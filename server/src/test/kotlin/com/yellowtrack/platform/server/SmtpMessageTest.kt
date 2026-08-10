package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.mail.Email
import com.yellowtrack.platform.server.mail.MailConfig
import com.yellowtrack.platform.server.mail.SmtpMail
import jakarta.mail.internet.MimeMultipart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What actually goes on the wire.
 *
 * `Mailer` is an interface, and every other test in this project uses a recording
 * implementation that captures an [Email]. That proves what the application *asked* for and
 * says nothing about what a mail server would receive — which is where `List-Unsubscribe`
 * was silently lost when it was first added, because a recording mailer sees the header on
 * the object whether or not anything writes it.
 *
 * No socket here: the message is built and read back, which is the part with the mistakes in.
 */
class SmtpMessageTest {
    /** The header the whole deliverability change turns on. */
    @Test
    fun `extra headers reach the message`() {
        val message =
            build(
                Email(
                    to = "guest@example.test",
                    subject = "Your photographs",
                    body = "text",
                    headers = mapOf("List-Unsubscribe" to "<mailto:ada@harbourline.test?subject=Unsubscribe>"),
                ),
            )

        assertEquals(
            "<mailto:ada@harbourline.test?subject=Unsubscribe>",
            message.getHeader("List-Unsubscribe")?.single(),
        )
    }

    @Test
    fun `a message with no extra headers carries none`() {
        val message = build(Email(to = "guest@example.test", subject = "Your photographs", body = "text"))

        assertEquals(null, message.getHeader("List-Unsubscribe"))
    }

    @Test
    fun `the sender name and address are what was asked for`() {
        val message =
            build(
                Email(
                    to = "guest@example.test",
                    subject = "Your photographs",
                    body = "text",
                    fromName = "Harbourline Photography",
                    fromAddress = "photos@yellowtrackphotos.com",
                    replyTo = "ada@harbourline.test",
                ),
            )

        val from = message.from.single().toString()
        assertTrue("Harbourline Photography" in from, from)
        assertTrue("photos@yellowtrackphotos.com" in from, from)
        assertEquals("ada@harbourline.test", message.replyTo.single().toString())
    }

    /**
     * `multipart/alternative` is ordered worst to best and a client shows the last part it
     * can render, so reversing these sends plain text to everybody. Worth pinning because it
     * is invisible until somebody opens the message.
     */
    @Test
    fun `the html part comes after the text part`() {
        val message =
            build(
                Email(
                    to = "guest@example.test",
                    subject = "Your photographs",
                    body = "the plain words",
                    html = "<p>the rendered words</p>",
                ),
            )

        val parts = message.content as MimeMultipart

        assertEquals(2, parts.count)
        // The declared type rather than `isMimeType`, which consults a DataHandler that is
        // not resolved until the message is saved and answers false for a part whose
        // Content-Type reads `text/plain`.
        assertTrue(parts.getBodyPart(0).contentType.startsWith("text/plain"), parts.getBodyPart(0).contentType)
        assertTrue(parts.getBodyPart(1).contentType.startsWith("text/html"), parts.getBodyPart(1).contentType)
    }

    /** A reset is one line and has no second form, so it must not become a multipart. */
    @Test
    fun `a message with no html is not multipart`() {
        val message = build(Email(to = "guest@example.test", subject = "Your code", body = "123456"))

        assertNotNull(message.content)
        assertTrue(message.contentType.startsWith("text/plain"), message.contentType)
    }

    private fun build(email: Email) =
        SmtpMail(
            MailConfig(
                host = "localhost",
                port = 1025,
                username = null,
                password = null,
                fromAddress = "no-reply@yellowtrackstudios.com",
                fromName = "Yellow Track",
                useTls = false,
            ),
        ).build(email)
}
