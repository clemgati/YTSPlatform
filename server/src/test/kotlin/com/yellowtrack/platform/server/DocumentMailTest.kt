package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.auth.Accounts
import com.yellowtrack.platform.server.document.DocumentMail
import com.yellowtrack.platform.server.document.SendRefused
import com.yellowtrack.platform.server.mail.Email
import com.yellowtrack.platform.server.mail.Mailer
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What actually leaves, when a studio sends a document to its client.
 *
 * Against the real Postgres, because the sender's identity is read from `studio_profile` and
 * the whole point of ADR 0011 is which parts of it end up where.
 */
class DocumentMailTest {
    /**
     * The decision the ADR turns on. The address has to stay this deployment's — SES signs
     * for the verified domain — while the studio's name and reply address carry its identity.
     */
    @Test
    fun `sends as the deployment's address under the studio's name`() {
        val studio = studioWithProfile(email = "ada@harbourline.photography")
        val mailer = RecordingMailer()

        documentMail(mailer).send(studio, "client@example.com", "Invoice INV-004", "<p>Owed</p>", "Owed")

        val sent = assertNotNull(mailer.last)
        assertEquals("clement@yellowtrackstudios.com", sent.fromAddress, "a studio's own address would not align")
        assertEquals("Harbourline Photography", sent.fromName, "the client reads the name, not the address")
        assertEquals("ada@harbourline.photography", sent.replyTo, "a reply has to reach the studio, not the server")
    }

    /** Nothing else keeps the rendered body, so the studio's copy is the only record. */
    @Test
    fun `copies the studio on what it sent`() {
        val studio = studioWithProfile(email = "ada@harbourline.photography")
        val mailer = RecordingMailer()

        documentMail(mailer).send(studio, "client@example.com", "Invoice", "<p>Owed</p>", "Owed")

        assertEquals("ada@harbourline.photography", mailer.last?.cc)
    }

    @Test
    fun `sends both the page and the text`() {
        val studio = studioWithProfile(email = "ada@harbourline.photography")
        val mailer = RecordingMailer()

        documentMail(mailer).send(studio, "client@example.com", "Invoice", "<p>Owed</p>", "Owed")

        assertEquals("<p>Owed</p>", mailer.last?.html)
        assertEquals("Owed", mailer.last?.body, "a client that cannot render html still has to get the document")
    }

    /**
     * Refused rather than sent from a mailbox the studio does not read. A client's reply to
     * an invoice going nowhere is worse than a send that did not happen.
     */
    @Test
    fun `refuses a studio with no email of its own`() {
        val studio = studioWithProfile(email = null)
        val mailer = RecordingMailer()

        assertFailsWith<SendRefused.NoStudioEmail> {
            documentMail(mailer).send(studio, "client@example.com", "Invoice", "<p>Owed</p>", "Owed")
        }
        assertTrue(mailer.sent.isEmpty(), "nothing should have left")
    }

    // -- The limit that keeps this from being a relay --------------------------------------

    @Test
    fun `stops a studio after its daily limit`() {
        val studio = studioWithProfile(email = "ada@harbourline.photography")
        val mail = documentMail(RecordingMailer(), limit = 3)

        repeat(3) { mail.send(studio, "client@example.com", "Invoice", "<p>x</p>", "x") }

        val refused =
            assertFailsWith<SendRefused.TooMany> {
                mail.send(studio, "client@example.com", "Invoice", "<p>x</p>", "x")
            }
        assertEquals(3, refused.limit, "the message has to name the limit, or it cannot be acted on")
    }

    /** One studio's sending must not spend another's allowance. */
    @Test
    fun `counts each studio separately`() {
        val mine = studioWithProfile(email = "ada@harbourline.photography")
        val theirs = studioWithProfile(email = "sam@okafor.photography")
        val mail = documentMail(RecordingMailer(), limit = 2)

        repeat(2) { mail.send(mine, "client@example.com", "Invoice", "<p>x</p>", "x") }

        // Not an exception: the other studio has sent nothing.
        mail.send(theirs, "client@example.com", "Invoice", "<p>x</p>", "x")
    }

    /**
     * Counted before the send, not after. A limit applied to successes only is a limit a
     * broken mail server switches off — and the abuse it exists for does not care whether the
     * messages arrived.
     */
    @Test
    fun `counts a send that failed`() {
        val studio = studioWithProfile(email = "ada@harbourline.photography")
        val mail = documentMail(BrokenMailer(), limit = 2)

        repeat(2) {
            assertFailsWith<SendRefused.Failed> {
                mail.send(studio, "client@example.com", "Invoice", "<p>x</p>", "x")
            }
        }

        assertFailsWith<SendRefused.TooMany> {
            mail.send(studio, "client@example.com", "Invoice", "<p>x</p>", "x")
        }
    }

    @Test
    fun `refuses when the server has no sender configured`() {
        val studio = studioWithProfile(email = "ada@harbourline.photography")

        assertFailsWith<SendRefused.NotConfigured> {
            DocumentMail(TestDatabase.database, RecordingMailer(), fromAddress = null)
                .send(studio, "client@example.com", "Invoice", "<p>x</p>", "x")
        }
    }

    // -- Fixtures ----------------------------------------------------------------------------

    private fun documentMail(
        mailer: Mailer,
        limit: Int = DocumentMail.DEFAULT_DAILY_LIMIT,
    ) = DocumentMail(
        database = TestDatabase.database,
        mailer = mailer,
        fromAddress = "clement@yellowtrackstudios.com",
        dailyLimit = limit,
    )

    private fun studioWithProfile(email: String?): String {
        val signedIn =
            Accounts(TestDatabase.database).signUp(
                "ada-${UUID.randomUUID()}@harbourline.test",
                "a long enough password",
                "Ada Okafor",
                "Harbourline Photography",
            )

        val now = System.currentTimeMillis()
        TestDatabase.database.inStudio(signedIn.studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO studio_profile(id, studio_id, name, email, created_at, updated_at, version, server_seq)
                    VALUES (?, ?, 'Harbourline Photography', ?, ?, ?, 1, 0)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, UUID.randomUUID().toString())
                    statement.setString(2, signedIn.studioId)
                    statement.setString(3, email)
                    statement.setLong(4, now)
                    statement.setLong(5, now)
                    statement.executeUpdate()
                }
        }

        return signedIn.studioId
    }

    private class RecordingMailer : Mailer {
        val sent = mutableListOf<Email>()

        val last: Email? get() = sent.lastOrNull()

        override fun send(email: Email) {
            sent += email
        }
    }

    private class BrokenMailer : Mailer {
        override fun send(email: Email): Unit = error("no route to host")
    }
}
