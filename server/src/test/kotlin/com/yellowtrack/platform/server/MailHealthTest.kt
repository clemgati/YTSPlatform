package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.mail.Email
import com.yellowtrack.platform.server.mail.MailHealth
import com.yellowtrack.platform.server.mail.Mailer
import com.yellowtrack.platform.server.mail.MonitoredMailer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MailHealthTest {
    @Test
    fun `nothing sent yet is unproved rather than healthy`() {
        val health = MailHealth()

        assertNull(health.lastSucceededAt, "a process that has sent nothing cannot claim a success")
        assertNull(health.lastFailure)
    }

    @Test
    fun `a send that works is recorded`() {
        val health = MailHealth(now = { 1_000L })
        MonitoredMailer(WorkingMailer(), health).send(anEmail())

        assertEquals(1_000L, health.lastSucceededAt)
        assertNull(health.lastFailure)
    }

    @Test
    fun `a refused send is recorded and still thrown`() {
        val health = MailHealth()
        val mailer = MonitoredMailer(BrokenMailer(IllegalStateException("535 authentication failed")), health)

        assertFailsWith<IllegalStateException> { mailer.send(anEmail()) }

        val failure = assertNotNull(health.lastFailure)
        assertTrue("535 authentication failed" in failure, "the reason has to survive: $failure")
        assertTrue("IllegalStateException" in failure, "the type is worth keeping too: $failure")
    }

    /**
     * The question this answers is "is mail broken now", not "has mail ever broken". One
     * refused message with a working server either side of it is not worth waking anybody.
     */
    @Test
    fun `a success clears an earlier failure`() {
        val health = MailHealth()
        assertFailsWith<IllegalStateException> {
            MonitoredMailer(BrokenMailer(IllegalStateException("temporary")), health).send(anEmail())
        }
        assertNotNull(health.lastFailure)

        MonitoredMailer(WorkingMailer(), health).send(anEmail())

        assertNull(health.lastFailure, "mail works again, so nothing should still be reported as broken")
    }

    /** A failure after a success has to show, or a credential expiring goes unnoticed. */
    @Test
    fun `a failure after a success is reported`() {
        val health = MailHealth(now = { 1_000L })
        MonitoredMailer(WorkingMailer(), health).send(anEmail())

        assertFailsWith<IllegalStateException> {
            MonitoredMailer(BrokenMailer(IllegalStateException("credential expired")), health).send(anEmail())
        }

        assertNotNull(health.lastFailure)
        assertEquals(1_000L, health.lastSucceededAt, "when it last worked is still a fact worth keeping")
    }

    @Test
    fun `an exception with no message still says something`() {
        val health = MailHealth()
        assertFailsWith<RuntimeException> {
            MonitoredMailer(BrokenMailer(RuntimeException()), health).send(anEmail())
        }

        val failure = assertNotNull(health.lastFailure)
        assertTrue("RuntimeException" in failure, failure)
    }

    /**
     * `verify-deployment.sh` reads these two out of `/ready` with `sed`, so a rename here is
     * a check that silently stops checking — the exact failure this whole change is about.
     * Asserted against the real serialised form rather than a hand-written string.
     */
    @Test
    fun `readiness serialises the field names the deployment check greps for`() {
        val failing =
            apiJson.encodeToString(
                Readiness(database = true, mail = true, mailError = "MessagingException: 535 refused"),
            )

        assertTrue("\"mailError\":\"MessagingException: 535 refused\"" in failing, failing)

        val working =
            apiJson.encodeToString(Readiness(database = true, mail = true, mailLastSucceededAt = 1_785_911_364L))

        assertTrue("\"mailLastSucceededAt\":1785911364" in working, working)

        // Nothing sent yet must not look like a failure to the script, which treats any
        // quoted mailError as one.
        val unproved = apiJson.encodeToString(Readiness(database = true, mail = true))
        assertTrue("\"mailError\":\"" !in unproved, unproved)
    }

    private fun anEmail() = Email(to = "ada@harbourline.test", subject = "code", body = "12345")

    private class WorkingMailer : Mailer {
        override fun send(email: Email) = Unit
    }

    private class BrokenMailer(
        private val cause: Throwable,
    ) : Mailer {
        override fun send(email: Email): Unit = throw cause
    }
}
