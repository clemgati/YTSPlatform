package com.yellowtrack.platform.server.auth

import com.yellowtrack.platform.server.Database
import com.yellowtrack.platform.server.mail.Email
import com.yellowtrack.platform.server.mail.Mailer
import java.security.SecureRandom
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Why a reset was refused. Deliberately one value: see [PasswordResets.reset]. */
class ResetRefused : Exception("that code is not usable")

/**
 * Getting back into an account whose password is gone.
 *
 * ADR 0010. The three properties worth knowing before changing anything here:
 *
 * 1. **Requesting never reveals whether the address exists.** [request] does the same work
 *    and answers the same way either way — otherwise this is the account-existence oracle
 *    that sign-in was deliberately built not to be, reopened at a different door.
 * 2. **A code is single-use and short-lived**, stored as a digest, and requesting a new one
 *    supersedes any outstanding one.
 * 3. **A completed reset revokes every session.** If the password was reset because somebody
 *    else had it, leaving that person signed in on their own device defeats the exercise.
 */
class PasswordResets(
    private val database: Database,
    private val mailer: Mailer?,
    private val now: () -> Long = System::currentTimeMillis,
    private val lifetime: Duration = DEFAULT_LIFETIME,
    private val onSendFailure: (Throwable) -> Unit = { it.printStackTrace() },
) {
    /**
     * Sends a code, if there is anybody to send it to.
     *
     * Returns nothing in every case. The caller cannot distinguish an unknown address, a
     * known one, or a mail server that refused the message, because any difference between
     * those is a difference somebody can measure.
     */
    fun request(email: String) {
        val normalised = Accounts.normaliseEmail(email)

        val target =
            database.unscoped { connection ->
                val account =
                    connection
                        .prepareStatement(
                            "SELECT id, name FROM account WHERE email = ? AND deleted_at IS NULL",
                        ).use { statement ->
                            statement.setString(1, normalised)
                            statement.executeQuery().use { rows ->
                                if (rows.next()) rows.getString(1) to rows.getString(2) else null
                            }
                        } ?: return@unscoped null

                val (accountId, name) = account
                val timestamp = now()

                // Supersede rather than delete: what was outstanding is a fact about the
                // account, and a row that vanishes is a question nobody can re-ask.
                connection
                    .prepareStatement(
                        """
                        UPDATE password_reset SET superseded_at = ?
                        WHERE account_id = ? AND consumed_at IS NULL AND superseded_at IS NULL
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setLong(1, timestamp)
                        statement.setString(2, accountId)
                        statement.executeUpdate()
                    }

                val code = newCode()

                connection
                    .prepareStatement(
                        """
                        INSERT INTO password_reset(id, account_id, code_digest, created_at, expires_at)
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, UUID.randomUUID().toString())
                        statement.setString(2, accountId)
                        statement.setString(3, Tokens.digest(code))
                        statement.setLong(4, timestamp)
                        statement.setLong(5, timestamp + lifetime.inWholeMilliseconds)
                        statement.executeUpdate()
                    }

                Triple(normalised, name, code)
            } ?: return

        // Outside the transaction: a mail server that hangs should not hold a database
        // connection open, and a code that was stored but not delivered is recoverable by
        // asking again, while the reverse is not.
        runCatching { mailer?.send(resetEmail(target.first, target.second, target.third)) }
            .onFailure(onSendFailure)
    }

    /**
     * Sets a new password, if the code is good.
     *
     * Every refusal is the same exception. An expired code, a consumed one, a superseded
     * one, and one that never existed are four different facts and telling them apart tells
     * a stranger which addresses have accounts and which codes were real.
     */
    fun reset(
        email: String,
        code: String,
        newPassword: String,
    ) {
        val normalised = Accounts.normaliseEmail(email)
        val timestamp = now()

        database.unscoped { connection ->
            val resetId =
                connection
                    .prepareStatement(
                        """
                        SELECT r.id
                        FROM password_reset r
                        JOIN account a ON a.id = r.account_id
                        WHERE r.code_digest = ?
                          AND a.email = ?
                          AND a.deleted_at IS NULL
                          AND r.consumed_at IS NULL
                          AND r.superseded_at IS NULL
                          AND r.expires_at > ?
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, Tokens.digest(code))
                        // Matched against the address too, so a code alone is not enough —
                        // somebody who guessed a digest still has to know whose it is.
                        statement.setString(2, normalised)
                        statement.setLong(3, timestamp)
                        statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                    } ?: throw ResetRefused()

            val accountId =
                connection
                    .prepareStatement("SELECT account_id FROM password_reset WHERE id = ?")
                    .use { statement ->
                        statement.setString(1, resetId)
                        statement.executeQuery().use { rows ->
                            rows.next()
                            rows.getString(1)
                        }
                    }

            connection
                .prepareStatement(
                    "UPDATE account SET password_hash = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                ).use { statement ->
                    statement.setString(1, Passwords.hash(newPassword))
                    statement.setLong(2, timestamp)
                    statement.setString(3, accountId)
                    statement.executeUpdate()
                }

            // Consumed before the sessions go, so a crash between the two leaves the code
            // spent rather than reusable.
            connection
                .prepareStatement("UPDATE password_reset SET consumed_at = ? WHERE id = ?")
                .use { statement ->
                    statement.setLong(1, timestamp)
                    statement.setString(2, resetId)
                    statement.executeUpdate()
                }

            // ADR 0010 decision 4. Includes the device doing the resetting, which is the
            // correct trade and is worth saying in the interface.
            connection
                .prepareStatement(
                    "UPDATE auth_session SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL",
                ).use { statement ->
                    statement.setLong(1, timestamp)
                    statement.setString(2, accountId)
                    statement.executeUpdate()
                }
        }
    }

    /**
     * A code a person has to read off a screen and type into another one.
     *
     * Ambiguous characters are left out of the alphabet — 0/O and 1/I/L cost more in
     * mistyped codes than they add in entropy. Ten characters from a 32-letter alphabet is
     * about fifty bits, against a code that is single-use, expires in an hour, and is
     * useless without knowing the address it belongs to.
     */
    private fun newCode(): String =
        (1..CODE_LENGTH)
            .map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .joinToString("")
            .chunked(CHUNK)
            .joinToString("-")

    private fun resetEmail(
        to: String,
        name: String,
        code: String,
    ) = Email(
        to = to,
        subject = "Your Yellow Track reset code",
        body =
            """
            Hello $name,

            Somebody asked to reset the password on your Yellow Track account. Enter this
            code in the application:

                $code

            It works once and expires in an hour.

            Resetting your password signs you out on every device, including the ones you
            are holding, so you will need to sign in again.

            If this was not you, nothing has changed and you can ignore this. Your password
            is unchanged until the code is used.
            """.trimIndent(),
    )

    private companion object {
        val DEFAULT_LIFETIME: Duration = 1.hours

        /** No 0/O, no 1/I/L: cheaper in entropy than in mistyped codes. */
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val CODE_LENGTH = 10
        const val CHUNK = 5

        val random = SecureRandom()
    }
}
