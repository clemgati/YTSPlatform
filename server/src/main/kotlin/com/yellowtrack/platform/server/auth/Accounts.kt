package com.yellowtrack.platform.server.auth

import com.yellowtrack.platform.server.Database
import java.sql.Connection
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/** A person, as the authentication path needs them. */
data class Account(
    val id: String,
    val email: String,
    val name: String,
    val passwordHash: String?,
)

/** A signed-in device, resolved from a presented token. */
data class AuthenticatedSession(
    val sessionId: String,
    val accountId: String,
    val studioId: String,
)

/** Who the caller is, resolved fresh from a session. */
data class Whoami(
    val account: Account,
    val studioId: String,
    val studioName: String,
)

/** What sign-up and sign-in hand back. */
data class SignedIn(
    val token: String,
    val account: Account,
    val studioId: String,
    val studioName: String,
    val expiresAt: Long,
)

/** Why an attempt was refused. Deliberately coarse — see [Accounts.signIn]. */
sealed interface SignInFailure {
    data object BadCredentials : SignInFailure

    data object NoStudio : SignInFailure
}

class EmailAlreadyRegistered : Exception("that email address already has an account")

/**
 * The authentication path, and the only code that reaches the account tables.
 *
 * Everything here runs through [Database.unscoped], because these are the tables that
 * decide which studio a request acts as and so cannot themselves be guarded by a policy
 * keyed on that studio. ADR 0009 decision 7 sets out why that hole exists and why this
 * file is the part of the system most worth reading line by line.
 */
class Accounts(
    private val database: Database,
    private val now: () -> Long = System::currentTimeMillis,
    private val sessionLifetime: Duration = DEFAULT_SESSION_LIFETIME,
) {
    /**
     * Registers a person, creates the studio they own, and signs them in.
     *
     * Sign-up creates a studio because there is no other way to get one yet: joining an
     * existing studio needs invitations, which arrive with roles in 0.8.0.
     *
     * All four writes share one transaction. A half-made account — a studio with no owner,
     * or a person belonging to nothing — is not a state anything here knows how to
     * recover from.
     */
    fun signUp(
        email: String,
        password: String,
        name: String,
        studioName: String,
    ): SignedIn =
        database.unscoped { connection ->
            val normalised = normaliseEmail(email)
            if (findByEmail(connection, normalised) != null) throw EmailAlreadyRegistered()

            val timestamp = now()
            val account =
                Account(
                    id = UUID.randomUUID().toString(),
                    email = normalised,
                    name = name.trim(),
                    passwordHash = Passwords.hash(password),
                )
            val studioId = UUID.randomUUID().toString()

            connection
                .prepareStatement(
                    "INSERT INTO studio(id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, studioId)
                    statement.setString(2, studioName.trim())
                    statement.setLong(3, timestamp)
                    statement.setLong(4, timestamp)
                    statement.executeUpdate()
                }

            connection
                .prepareStatement(
                    """
                    INSERT INTO account(id, email, name, password_hash, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, account.id)
                    statement.setString(2, account.email)
                    statement.setString(3, account.name)
                    statement.setString(4, account.passwordHash)
                    statement.setLong(5, timestamp)
                    statement.setLong(6, timestamp)
                    statement.executeUpdate()
                }

            connection
                .prepareStatement(
                    """
                    INSERT INTO studio_member(id, studio_id, account_id, role, created_at, updated_at)
                    VALUES (?, ?, ?, 'Owner', ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, UUID.randomUUID().toString())
                    statement.setString(2, studioId)
                    statement.setString(3, account.id)
                    statement.setLong(4, timestamp)
                    statement.setLong(5, timestamp)
                    statement.executeUpdate()
                }

            val (token, expiresAt) = openSession(connection, account.id, studioId, timestamp)
            SignedIn(token, account, studioId, studioName.trim(), expiresAt)
        }

    /**
     * Checks a password and opens a session.
     *
     * An unknown email and a wrong password both return [SignInFailure.BadCredentials],
     * and an unknown email still pays for a hash comparison against a dummy. Answering
     * faster for an address that does not exist would turn this endpoint into a way to
     * ask which of a studio's clients has an account here.
     */
    fun signIn(
        email: String,
        password: String,
    ): Result<SignedIn> =
        database.unscoped { connection ->
            val account = findByEmail(connection, normaliseEmail(email))

            val verified =
                when (val hash = account?.passwordHash) {
                    null -> {
                        Passwords.verify(password, DUMMY_HASH)
                        false
                    }
                    else -> Passwords.verify(password, hash)
                }

            if (account == null ||
                !verified
            ) {
                return@unscoped Result.failure(SignInRefused(SignInFailure.BadCredentials))
            }

            val membership =
                studioFor(connection, account.id)
                    ?: return@unscoped Result.failure(SignInRefused(SignInFailure.NoStudio))

            val timestamp = now()
            val (token, expiresAt) = openSession(connection, account.id, membership.first, timestamp)
            Result.success(SignedIn(token, account, membership.first, membership.second, expiresAt))
        }

    /**
     * Resolves a presented token, or null if it is unknown, expired or revoked.
     *
     * Looked up by digest, so a token that is not already in the table cannot be found by
     * one that is close to it.
     */
    fun authenticate(token: String): AuthenticatedSession? =
        database.unscoped { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT id, account_id, studio_id
                    FROM auth_session
                    WHERE token_digest = ? AND revoked_at IS NULL AND expires_at > ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, Tokens.digest(token))
                    statement.setLong(2, now())
                    statement.executeQuery().use { rows ->
                        if (!rows.next()) {
                            null
                        } else {
                            AuthenticatedSession(rows.getString(1), rows.getString(2), rows.getString(3))
                                .also { touch(connection, it.sessionId) }
                        }
                    }
                }
        }

    /**
     * Who a session belongs to, for `/auth/me`.
     *
     * Read fresh rather than carried in the token, so a renamed studio or a corrected name
     * is right on the next request instead of at the next sign-in.
     */
    fun whoami(session: AuthenticatedSession): Whoami? =
        database.unscoped { connection ->
            val account =
                connection
                    .prepareStatement(
                        "SELECT id, email, name, password_hash FROM account WHERE id = ? AND deleted_at IS NULL",
                    ).use { statement ->
                        statement.setString(1, session.accountId)
                        statement.executeQuery().use { rows ->
                            if (rows.next()) {
                                Account(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4))
                            } else {
                                null
                            }
                        }
                    } ?: return@unscoped null

            val studioName =
                connection
                    .prepareStatement("SELECT name FROM studio WHERE id = ? AND deleted_at IS NULL")
                    .use { statement ->
                        statement.setString(1, session.studioId)
                        statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
                    } ?: return@unscoped null

            Whoami(account, session.studioId, studioName)
        }

    /** Ends one session. The other devices signed in to the same account keep working. */
    fun signOut(sessionId: String) {
        database.unscoped { connection ->
            connection
                .prepareStatement("UPDATE auth_session SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL")
                .use { statement ->
                    statement.setLong(1, now())
                    statement.setString(2, sessionId)
                    statement.executeUpdate()
                }
        }
    }

    private fun openSession(
        connection: Connection,
        accountId: String,
        studioId: String,
        timestamp: Long,
    ): Pair<String, Long> {
        val token = Tokens.issue()
        val expiresAt = timestamp + sessionLifetime.inWholeMilliseconds

        connection
            .prepareStatement(
                """
                INSERT INTO auth_session(id, account_id, studio_id, token_digest, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, accountId)
                statement.setString(3, studioId)
                statement.setString(4, Tokens.digest(token))
                statement.setLong(5, timestamp)
                statement.setLong(6, expiresAt)
                statement.executeUpdate()
            }

        return token to expiresAt
    }

    private fun touch(
        connection: Connection,
        sessionId: String,
    ) {
        connection.prepareStatement("UPDATE auth_session SET last_used_at = ? WHERE id = ?").use { statement ->
            statement.setLong(1, now())
            statement.setString(2, sessionId)
            statement.executeUpdate()
        }
    }

    private fun findByEmail(
        connection: Connection,
        email: String,
    ): Account? =
        connection
            .prepareStatement(
                "SELECT id, email, name, password_hash FROM account WHERE email = ? AND deleted_at IS NULL",
            ).use { statement ->
                statement.setString(1, email)
                statement.executeQuery().use { rows ->
                    if (rows.next()) {
                        Account(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4))
                    } else {
                        null
                    }
                }
            }

    /** The studio this account acts as. Exactly one until invitations arrive in 0.8.0. */
    private fun studioFor(
        connection: Connection,
        accountId: String,
    ): Pair<String, String>? =
        connection
            .prepareStatement(
                """
                SELECT studio.id, studio.name
                FROM studio_member
                JOIN studio ON studio.id = studio_member.studio_id
                WHERE studio_member.account_id = ?
                  AND studio_member.deleted_at IS NULL
                  AND studio.deleted_at IS NULL
                ORDER BY studio_member.created_at
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, accountId)
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getString(1) to rows.getString(2) else null
                }
            }

    companion object {
        /**
         * Long, because a device that has been in a field all day should reconcile that
         * evening rather than meet a sign-in prompt. Affordable only because `revoked_at`
         * means a lost phone can be cut off — see ADR 0009 decision 4.
         */
        val DEFAULT_SESSION_LIFETIME: Duration = 90.days

        /**
         * Compared against when the email is unknown, so that answering takes about as
         * long either way.
         */
        private val DUMMY_HASH = Passwords.hash("this password matches nothing")

        /** Lowercased and trimmed at the boundary, so `UNIQUE(email)` means what it looks like. */
        fun normaliseEmail(email: String): String = email.trim().lowercase()
    }
}

/** Carries [SignInFailure] through [Result]. */
class SignInRefused(
    val failure: SignInFailure,
) : Exception("sign-in refused")
