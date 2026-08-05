package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.account.StudioExport
import com.yellowtrack.platform.server.auth.Accounts
import com.yellowtrack.platform.server.sync.SyncedEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The export, against the real Postgres and the real policies.
 *
 * Written against a database rather than a mock on purpose: the property that matters is
 * that row level security scopes the read, and nothing but Postgres can demonstrate that.
 */
class StudioExportTest {
    /**
     * The one that would be catastrophic. Two studios, one export, and the other studio's
     * client must not be in it — enforced by the policies rather than by a `WHERE` clause
     * this class could have got wrong.
     */
    @Test
    fun `never includes another studio's records`() {
        val mine = studioWithClient("Okafor Weddings")
        val theirs = studioWithClient("Harbourline Portraits")

        val exported = StudioExport(TestDatabase.database).of(mine.studioId, mine.accountId)
        val names = clientNamesIn(exported)

        assertTrue("Okafor Weddings" in names, "my own client should be there: $names")
        assertTrue("Harbourline Portraits" !in names, "another studio's client leaked into the export: $names")
        assertTrue(theirs.studioId != mine.studioId)
    }

    @Test
    fun `includes the studio's own records`() {
        val mine = studioWithClient("Okafor Weddings")

        val exported = StudioExport(TestDatabase.database).of(mine.studioId, mine.accountId)

        assertEquals(listOf("Okafor Weddings"), clientNamesIn(exported))
    }

    /**
     * A new entity added to sync must appear here without anybody remembering to add it.
     * The failure this guards against is silent: an export that quietly omits a table looks
     * exactly like a studio that never used that feature.
     */
    @Test
    fun `covers every entity that sync knows about`() {
        val mine = studioWithClient("Okafor Weddings")

        val records = StudioExport(TestDatabase.database).of(mine.studioId, mine.accountId)["records"]!!.jsonObject

        SyncedEntity.all.forEach { entity ->
            assertNotNull(records[entity.table], "${entity.table} is missing from the export")
        }
        assertEquals(SyncedEntity.all.size, records.size, "the export and sync disagree about how many tables exist")
    }

    /** Empty is a fact worth stating. An absent key and an empty list read differently. */
    @Test
    fun `names a table the studio has never used rather than omitting it`() {
        val mine = studioWithClient("Okafor Weddings")

        val records = StudioExport(TestDatabase.database).of(mine.studioId, mine.accountId)["records"]!!.jsonObject

        assertEquals(0, records["lead"]!!.jsonArray.size, "no leads were created, so it should be present and empty")
    }

    @Test
    fun `identifies itself and says who it belongs to`() {
        val mine = studioWithClient("Okafor Weddings")

        val exported =
            StudioExport(TestDatabase.database, now = { 1_700_000_000_000L })
                .of(mine.studioId, mine.accountId)

        assertEquals("Yellow Track", exported["application"]!!.jsonPrimitive.content)
        assertEquals(1_700_000_000_000L, exported["exportedAt"]!!.jsonPrimitive.content.toLong())
        assertEquals(mine.studioId, exported["studioId"]!!.jsonPrimitive.content)
        assertEquals(mine.email, exported["account"]!!.jsonObject["email"]!!.jsonPrimitive.content)
        assertEquals("Harbourline Photography", exported["studio"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    /** A file people email to themselves is the last place a password hash should be. */
    @Test
    fun `carries no password hash`() {
        val mine = studioWithClient("Okafor Weddings")

        val exported = StudioExport(TestDatabase.database).of(mine.studioId, mine.accountId)

        assertTrue("password" !in exported.toString().lowercase(), "the export mentions a password")
    }

    /** Records must be JSON, not one long escaped string that nothing can read. */
    @Test
    fun `writes records as json rather than as encoded text`() {
        val mine = studioWithClient("Okafor Weddings")

        val client =
            StudioExport(TestDatabase.database)
                .of(mine.studioId, mine.accountId)["records"]!!
                .jsonObject["client"]!!
                .jsonArray
                .single()

        assertEquals("Okafor Weddings", client.jsonObject["accountName"]!!.jsonPrimitive.content)
    }

    private fun clientNamesIn(exported: JsonObject) =
        exported["records"]!!
            .jsonObject["client"]!!
            .jsonArray
            .map { it.jsonObject["accountName"]!!.jsonPrimitive.content }

    private data class Studio(
        val studioId: String,
        val accountId: String,
        val email: String,
    )

    private fun studioWithClient(clientName: String): Studio {
        val email = "ada-${UUID.randomUUID()}@harbourline.test"
        val accounts = Accounts(TestDatabase.database)
        val signedIn = accounts.signUp(email, "a long enough password", "Ada Okafor", "Harbourline Photography")

        val now = System.currentTimeMillis()
        TestDatabase.database.inStudio(signedIn.studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO client(id, studio_id, account_name, account_type, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'Individual', ?, ?, 1)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, UUID.randomUUID().toString())
                    statement.setString(2, signedIn.studioId)
                    statement.setString(3, clientName)
                    statement.setLong(4, now)
                    statement.setLong(5, now)
                    statement.executeUpdate()
                }
        }

        return Studio(signedIn.studioId, signedIn.account.id, email)
    }
}
