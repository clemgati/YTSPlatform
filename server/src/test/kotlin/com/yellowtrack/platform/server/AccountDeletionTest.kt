package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.account.AccountDeletion
import com.yellowtrack.platform.server.auth.Accounts
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * Deletion, against the real Postgres and the real policies.
 *
 * The purge cannot be tested anywhere else. Every business table is guarded by a policy
 * comparing `studio_id` against a setting, and a `DELETE` outside that scope removes nothing
 * while reporting success — so a test with a mocked database would pass on code that deletes
 * a studio row and leaves every client and invoice behind.
 */
class AccountDeletionTest {
    private val password = "a long enough password"

    // -- Asking to be deleted --------------------------------------------------------------

    @Test
    fun `refuses a wrong password`() {
        val studio = studioWithClient()
        val deletion = AccountDeletion(TestDatabase.database)

        assertNull(deletion.request(studio.accountId, studio.studioId, "not the password"))
        assertEquals(1, clientCount(studio.studioId), "nothing should have been touched")
        assertNull(deletedAtOfStudio(studio.studioId))
    }

    @Test
    fun `marks the studio and the account deleted`() {
        val studio = studioWithClient()

        val done = AccountDeletion(TestDatabase.database).request(studio.accountId, studio.studioId, password)

        assertNotNull(done)
        assertNotNull(deletedAtOfStudio(studio.studioId))
        assertNotNull(deletedAtOfAccount(studio.accountId))
    }

    /** A deleted studio must not be reachable from a device that was signed in when it went. */
    @Test
    fun `revokes every session`() {
        val studio = studioWithClient()
        assertEquals(0, liveSessionCount(studio.accountId).let { 1 - it }, "should start with a live session")

        AccountDeletion(TestDatabase.database).request(studio.accountId, studio.studioId, password)

        assertEquals(0, liveSessionCount(studio.accountId), "a deleted studio must sign every device out")
    }

    /** The whole point of the window: the records are still there to be put back. */
    @Test
    fun `keeps the records until the window has passed`() {
        val studio = studioWithClient()
        val deletion = AccountDeletion(TestDatabase.database, retention = 30.days)

        deletion.request(studio.accountId, studio.studioId, password)

        assertEquals(1, clientCount(studio.studioId), "deleting must not remove anything yet")
        assertTrue(deletion.purge().isEmpty, "nothing is old enough to purge")
        assertEquals(1, clientCount(studio.studioId))
    }

    // -- The purge ---------------------------------------------------------------------------

    /**
     * The one that catches the mistake worth catching. Written against the row *count*
     * rather than the report, because a purge running outside the studio's row level
     * security scope deletes nothing, returns a number, and looks exactly like success.
     */
    @Test
    fun `removes the records once the window has passed`() {
        val studio = studioWithClient()
        // A window of nothing, so what was deleted a moment ago is already past it.
        val deletion = AccountDeletion(TestDatabase.database, retention = 1.milliseconds)

        deletion.request(studio.accountId, studio.studioId, password)
        Thread.sleep(5)
        val report = deletion.purge()

        assertTrue(report.studios >= 1, "the studio should have been purged")
        assertEquals(0, clientCount(studio.studioId), "the client rows are still there — was the purge scoped?")
        assertNull(studioRow(studio.studioId), "the studio row should be gone")
        assertNull(accountRow(studio.accountId), "the account should be gone with its last studio")
        assertEquals(0, sessionCount(studio.accountId), "sessions should be gone")
    }

    /** A live studio is not deleted, whatever else is being purged around it. */
    @Test
    fun `never touches a studio that was not deleted`() {
        val doomed = studioWithClient()
        val living = studioWithClient()
        val deletion = AccountDeletion(TestDatabase.database, retention = 1.milliseconds)

        deletion.request(doomed.accountId, doomed.studioId, password)
        Thread.sleep(5)
        deletion.purge()

        assertEquals(1, clientCount(living.studioId), "another studio's records were purged")
        assertNotNull(studioRow(living.studioId))
        assertNotNull(accountRow(living.accountId))
    }

    @Test
    fun `purging twice is not an error`() {
        val studio = studioWithClient()
        val deletion = AccountDeletion(TestDatabase.database, retention = 1.milliseconds)

        deletion.request(studio.accountId, studio.studioId, password)
        Thread.sleep(5)
        deletion.purge()

        assertTrue(deletion.purge().isEmpty, "the second run has nothing left to do")
    }

    // -- Reading the database directly ---------------------------------------------------------

    private data class Studio(
        val studioId: String,
        val accountId: String,
    )

    private fun studioWithClient(): Studio {
        val signedIn =
            Accounts(TestDatabase.database).signUp(
                "ada-${UUID.randomUUID()}@harbourline.test",
                password,
                "Ada Okafor",
                "Harbourline Photography",
            )

        val now = System.currentTimeMillis()
        TestDatabase.database.inStudio(signedIn.studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO client(id, studio_id, account_name, account_type, created_at, updated_at, version)
                    VALUES (?, ?, 'Okafor Weddings', 'Individual', ?, ?, 1)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, UUID.randomUUID().toString())
                    statement.setString(2, signedIn.studioId)
                    statement.setLong(3, now)
                    statement.setLong(4, now)
                    statement.executeUpdate()
                }
        }

        return Studio(signedIn.studioId, signedIn.account.id)
    }

    /** Counted inside the studio's own scope, or the policy hides what is being asserted about. */
    private fun clientCount(studioId: String): Int =
        TestDatabase.database.inStudio(studioId) { connection ->
            connection.prepareStatement("SELECT count(*) FROM client WHERE studio_id = ?").use { statement ->
                statement.setString(1, studioId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    private fun studioRow(studioId: String) = singleValue("SELECT id FROM studio WHERE id = ?", studioId)

    private fun accountRow(accountId: String) = singleValue("SELECT id FROM account WHERE id = ?", accountId)

    private fun deletedAtOfStudio(studioId: String) =
        singleValue("SELECT deleted_at FROM studio WHERE id = ? AND deleted_at IS NOT NULL", studioId)

    private fun deletedAtOfAccount(accountId: String) =
        singleValue("SELECT deleted_at FROM account WHERE id = ? AND deleted_at IS NOT NULL", accountId)

    private fun singleValue(
        sql: String,
        id: String,
    ): String? =
        TestDatabase.database.unscoped { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        }

    private fun sessionCount(accountId: String): Int =
        count("SELECT count(*) FROM auth_session WHERE account_id = ?", accountId)

    private fun liveSessionCount(accountId: String): Int =
        count("SELECT count(*) FROM auth_session WHERE account_id = ? AND revoked_at IS NULL", accountId)

    private fun count(
        sql: String,
        id: String,
    ): Int =
        TestDatabase.database.unscoped { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }
}
