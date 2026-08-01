package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.auth.Accounts
import com.yellowtrack.platform.server.auth.PasswordResets
import com.yellowtrack.platform.server.auth.ResetRefused
import com.yellowtrack.platform.server.mail.Email
import com.yellowtrack.platform.server.mail.Mailer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * Getting back into an account whose password is gone.
 *
 * Most of what is checked here is what the endpoint *does not* say. A reset flow that
 * answers differently for a known and an unknown address is an account-existence oracle,
 * and this application already refused to be one at sign-in — reopening it here would be
 * the same disclosure through a different door.
 */
class PasswordResetTest {
    // -- What it does ---------------------------------------------------------------------

    @Test
    fun `a code arrives and lets a new password be set`() {
        val world = world()
        val email = world.signUp()

        world.resets.request(email)
        val code = world.mailer.codeSentTo(email)

        world.resets.reset(email, code, "a brand new password")

        assertTrue(
            world.accounts.signIn(email, "a brand new password").isSuccess,
            "the whole point is being able to get back in",
        )
        assertTrue(
            world.accounts.signIn(email, ORIGINAL_PASSWORD).isFailure,
            "and the old password must stop working",
        )
    }

    @Test
    fun `the code is not stored, only its digest`() {
        val world = world()
        val email = world.signUp()
        world.resets.request(email)
        val code = world.mailer.codeSentTo(email)

        TestDatabase.connection().use { db ->
            db.prepareStatement("SELECT count(*) FROM password_reset WHERE code_digest = ?").use { statement ->
                statement.setString(1, code)
                statement.executeQuery().use { rows ->
                    rows.next()
                    assertEquals(
                        0,
                        rows.getInt(1),
                        "a copy of this table must not be a set of working codes",
                    )
                }
            }
        }
    }

    // -- What it refuses ------------------------------------------------------------------

    @Test
    fun `a code works once`() {
        val world = world()
        val email = world.signUp()
        world.resets.request(email)
        val code = world.mailer.codeSentTo(email)

        world.resets.reset(email, code, "a brand new password")

        assertFailsWith<ResetRefused>(
            "a code read from an inbox somebody else also has must not stay usable",
        ) {
            world.resets.reset(email, code, "somebody else's password")
        }
    }

    @Test
    fun `asking again invalidates the code already sent`() {
        val world = world()
        val email = world.signUp()

        world.resets.request(email)
        val first = world.mailer.codeSentTo(email)
        world.resets.request(email)
        val second = world.mailer.codeSentTo(email)

        assertNotEquals(first, second)
        assertFailsWith<ResetRefused>(
            "two live codes is one more than anybody needs, and one more chance for the " +
                "older one to be the leaked one",
        ) {
            world.resets.reset(email, first, "a brand new password")
        }

        world.resets.reset(email, second, "a brand new password")
    }

    @Test
    fun `an expired code is refused`() {
        val world = world(lifetimeHours = 0)
        val email = world.signUp()
        world.resets.request(email)
        val code = world.mailer.codeSentTo(email)

        assertFailsWith<ResetRefused>("an old message in a mailbox must not be a standing key") {
            world.resets.reset(email, code, "a brand new password")
        }
    }

    @Test
    fun `a code is useless without the address it belongs to`() {
        val world = world()
        val mine = world.signUp()
        val theirs = world.signUp()
        world.resets.request(mine)
        val code = world.mailer.codeSentTo(mine)

        assertFailsWith<ResetRefused>(
            "somebody who guessed a digest still has to know whose account it is",
        ) {
            world.resets.reset(theirs, code, "a brand new password")
        }
    }

    // -- What it will not tell you ---------------------------------------------------------

    @Test
    fun `requesting a reset for an address with no account does nothing and says nothing`() {
        val world = world()

        world.resets.request("nobody-${System.nanoTime()}@harbourline.test")

        assertTrue(
            world.mailer.sent.isEmpty(),
            "nothing to send, and the caller is told the same thing either way — see the " +
                "route, which answers 202 regardless",
        )
    }

    @Test
    fun `a mail server that refuses the message does not fail the request`() {
        val world = world(mailer = RefusingMailer())
        val email = world.signUp()

        // Does not throw. The caller cannot be told, because being told would distinguish
        // this from an unknown address.
        world.resets.request(email)

        assertTrue(world.failures.isNotEmpty(), "but it must reach the log, or it is perfectly silent on both ends")
    }

    // -- What a reset does to everything else ------------------------------------------------

    @Test
    fun `resetting signs out every device, including the one doing it`() {
        val world = world()
        val email = world.signUp()

        val laptop = world.accounts.signIn(email, ORIGINAL_PASSWORD).getOrThrow()
        val phone = world.accounts.signIn(email, ORIGINAL_PASSWORD).getOrThrow()
        assertNotNull(world.accounts.authenticate(laptop.token))
        assertNotNull(world.accounts.authenticate(phone.token))

        world.resets.request(email)
        world.resets.reset(email, world.mailer.codeSentTo(email), "a brand new password")

        assertNull(
            world.accounts.authenticate(laptop.token),
            "if the password was reset because somebody else had it, leaving them signed in " +
                "on their own device defeats the exercise",
        )
        assertNull(world.accounts.authenticate(phone.token))
    }

    @Test
    fun `the code is readable off a screen`() {
        val world = world()
        val email = world.signUp()
        world.resets.request(email)
        val code = world.mailer.codeSentTo(email)

        assertFalse(
            code.any { it in "01OIL" },
            "0/O and 1/I/L cost more in mistyped codes than they add in entropy",
        )
        assertTrue(code.contains('-'), "grouped, because a ten-character run is hard to keep your place in")
    }

    // -- Plumbing ---------------------------------------------------------------------------

    private fun assertNotNull(
        value: Any?,
        message: String = "expected a value",
    ) = assertTrue(value != null, message)

    private class RecordingMailer : Mailer {
        val sent = mutableListOf<Email>()

        override fun send(email: Email) {
            sent += email
        }

        /** Pulls the code back out of the message, the way a person reading it would. */
        fun codeSentTo(address: String): String =
            sent
                .last { it.to == address }
                .body
                .lineSequence()
                .map(String::trim)
                .first { it.matches(Regex("[A-Z0-9]{5}-[A-Z0-9]{5}")) }
    }

    private class RefusingMailer : Mailer {
        override fun send(email: Email): Unit = error("the mail server refused it")
    }

    private class World(
        val accounts: Accounts,
        val resets: PasswordResets,
        val mailer: RecordingMailer,
        val failures: MutableList<Throwable>,
    ) {
        fun signUp(): String {
            val email = "reset-${counter++}-${System.nanoTime()}@harbourline.test"
            accounts.signUp(email, ORIGINAL_PASSWORD, "Ada Okafor", "Harbourline Photography")
            return email
        }
    }

    private fun world(
        lifetimeHours: Int = 1,
        mailer: Mailer? = null,
    ): World {
        val recording = RecordingMailer()
        val failures = mutableListOf<Throwable>()

        return World(
            accounts = Accounts(TestDatabase.database),
            resets =
                PasswordResets(
                    database = TestDatabase.database,
                    mailer = mailer ?: recording,
                    lifetime = lifetimeHours.hours,
                    onSendFailure = { failures += it },
                ),
            mailer = recording,
            failures = failures,
        )
    }

    private companion object {
        const val ORIGINAL_PASSWORD = "the original password"
        var counter = 0
    }
}
