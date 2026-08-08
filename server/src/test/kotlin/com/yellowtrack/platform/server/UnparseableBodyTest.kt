package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.mail.describeUnparseableBody
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the log says when a posted body cannot be read.
 *
 * Tested because the message this replaces was the reason a working deployment looked
 * broken for half an hour: notifications were arriving and being dropped, and "could not
 * parse an SNS message" said nothing about which of the several possible causes it was. A
 * diagnostic that does not name the fix is a diagnostic that costs a debugging session, so
 * the wording is a property worth holding rather than a detail.
 */
class UnparseableBodyTest {
    /**
     * The case that actually happened. Raw message delivery strips the SNS envelope, so the
     * bare SES payload arrives with `notificationType` and none of the envelope fields.
     */
    @Test
    fun `names raw message delivery when the envelope has been stripped`() {
        val raw =
            """
            {"notificationType":"Delivery",
             "mail":{"timestamp":"2026-08-08T17:08:49.166Z"},
             "delivery":{"recipients":["someone@example.com"]}}
            """.trimIndent()

        val description = describeUnparseableBody(raw)

        assertTrue(
            description.contains("Raw message delivery", ignoreCase = true),
            "the fix has to be named, not described: was \"$description\"",
        )
        assertTrue(
            description.contains("signature", ignoreCase = true),
            "it should say why this can never be accepted rather than just how to change it",
        )
    }

    /**
     * The privacy property. An SES payload names the people it was sent to, and a log is
     * kept for fourteen days and read by whoever is on the box. Diagnosing a *shape* problem
     * does not need the addresses, and the row in `mail_notification` is where they belong —
     * there they can be deleted with the record rather than living on in a rotated file.
     */
    @Test
    fun `never puts a recipient address in the log`() {
        val raw =
            """
            {"notificationType":"Bounce",
             "bounce":{"bouncedRecipients":[{"emailAddress":"private.person@example.com"}]}}
            """.trimIndent()

        val description = describeUnparseableBody(raw)

        assertFalse(
            description.contains("private.person@example.com"),
            "a shape problem must not be diagnosed by printing somebody's address",
        )
    }

    /** An envelope of some other shape still says what arrived, by key rather than by value. */
    @Test
    fun `lists the keys when the shape is merely unexpected`() {
        val description = describeUnparseableBody("""{"Type":"Notification","Nonsense":1}""")

        assertTrue(description.contains("Nonsense"), "the keys are the diagnosis: was \"$description\"")
        assertTrue(description.contains("Type"))
    }

    /**
     * A body that is not JSON has no keys to report, so a prefix is used. This is the health
     * probe, the stray scanner and the truncated post — none of which carry a studio's data.
     */
    @Test
    fun `falls back to a prefix when the body is not json at all`() {
        val description = describeUnparseableBody("not json at all")

        assertTrue(description.contains("not a JSON object"), "was \"$description\"")
        assertTrue(description.contains("15 bytes"), "the length is worth having: was \"$description\"")
    }

    /** A JSON array is not an object either, and must not throw on the way to saying so. */
    @Test
    fun `handles a json array without failing`() {
        val description = describeUnparseableBody("""["not","an","object"]""")

        assertTrue(description.contains("not a JSON object"), "was \"$description\"")
    }

    /** An empty body is the commonest stray request and must not be a special case. */
    @Test
    fun `handles an empty body`() {
        val description = describeUnparseableBody("")

        assertTrue(description.contains("not a JSON object"), "was \"$description\"")
        assertTrue(description.contains("0 bytes"))
    }
}
