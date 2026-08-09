package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.account.AccountDeletion
import com.yellowtrack.platform.server.account.PurgeReport
import com.yellowtrack.platform.server.storage.ObjectStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The promise ADR 0009 makes, once photographs stop living in Postgres.
 *
 * A studio is told its records go for good thirty days after it deletes itself. The purge
 * has always removed rows; ADR 0013 puts photographs in a bucket, and a purge that only
 * removed rows would leave every one of them there — unreachable, since `stored_object` is
 * the only record of which keys belong to whom, and directly contrary to what the studio was
 * told.
 *
 * Written before anything uploads, deliberately. Adding it alongside the first upload would
 * mean changing the purge underneath a feature rather than before it.
 */
class StoredObjectPurgeTest {
    /** A store that remembers, and can be told to fail for particular keys. */
    private class RecordingStore(
        private val refuse: Set<String> = emptySet(),
    ) : ObjectStore {
        val stored = mutableSetOf<String>()
        val deleteAttempts = mutableListOf<String>()

        override fun put(
            key: String,
            contentType: String,
            bytes: ByteArray,
        ) {
            stored += key
        }

        override fun temporaryUrl(
            key: String,
            validFor: Duration,
        ): String = "https://example.invalid/$key"

        override fun delete(keys: List<String>): Set<String> {
            deleteAttempts += keys
            val gone = keys.filterNot { it in refuse }
            stored -= gone.toSet()

            return gone.toSet()
        }
    }

    /** The ordinary case: objects go, and so do the rows that named them. */
    @Test
    fun `purging a studio removes its objects as well as its rows`() {
        val studio = deletedStudioWithObjects(count = 2)
        val store = RecordingStore().also { it.stored += studio.keys }

        val report = purge(store)

        assertTrue(report.studios >= 1)
        assertEquals(
            emptySet(),
            store.stored.intersect(studio.keys.toSet()),
            "the studio's photographs are still in the bucket after it was told they were gone",
        )
        assertEquals(0, remainingObjectRows(studio.id), "the rows naming those keys should have gone too")
    }

    /**
     * The case that decides whether the promise is kept or merely reported.
     *
     * A key that will not delete keeps its row, so the next run finds it again. Removing the
     * row anyway would lose the only record that the object exists — it would be in the
     * bucket forever with nothing left that knows to look for it.
     */
    @Test
    fun `an object that cannot be deleted keeps its row`() {
        val studio = deletedStudioWithObjects(count = 2)
        val stubborn = studio.keys.first()
        val store = RecordingStore(refuse = setOf(stubborn)).also { it.stored += studio.keys }

        purge(store)

        assertTrue(stubborn in store.stored, "the fixture should have refused this one")
        assertEquals(
            1,
            remainingObjectRows(studio.id),
            "the row for an object still in the bucket must survive, or nothing knows it is there",
        )
        assertTrue(
            studioStillExists(studio.id),
            "the studio must survive too, so the next run finds it again — a studio reported purged " +
                "while its photographs are still in a bucket is the promise being broken quietly",
        )
    }

    /** A deployment with no bucket has nothing to delete, and its purge must still run. */
    @Test
    fun `a deployment with no storage still purges`() {
        val studio = deletedStudioWithObjects(count = 1)

        val report = AccountDeletion(TestDatabase.database, retention = 0.days, now = { Long.MAX_VALUE / 2 }).purge()

        assertTrue(report.studios >= 1)
        assertEquals(0, remainingObjectRows(studio.id), "Unconfigured answers deleted for every key")
    }

    // -- Fixtures --------------------------------------------------------------------------

    private data class Fixture(
        val id: String,
        val keys: List<String>,
    )

    private fun purge(store: ObjectStore): PurgeReport =
        AccountDeletion(
            TestDatabase.database,
            retention = 0.days,
            now = { Long.MAX_VALUE / 2 },
            objects = store,
        ).purge()

    /** A studio already past its retention, holding [count] objects. */
    private fun deletedStudioWithObjects(count: Int): Fixture {
        val id = "studio-${UUID.randomUUID()}"
        val keys = (1..count).map { "$id/photo-$it.jpg" }

        TestDatabase.connection().use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO studio(id, name, created_at, updated_at, deleted_at, version) VALUES (?, ?, 0, 0, 1, 1)",
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, "Purge fixture")
                    statement.executeUpdate()
                }

            keys.forEach { key ->
                connection
                    .prepareStatement(
                        """
                        INSERT INTO stored_object(id, studio_id, object_key, content_type, size_bytes, created_at)
                        VALUES (?, ?, ?, 'image/jpeg', 1, 0)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setString(1, UUID.randomUUID().toString())
                        statement.setString(2, id)
                        statement.setString(3, key)
                        statement.executeUpdate()
                    }
            }
        }

        return Fixture(id, keys)
    }

    private fun studioStillExists(studioId: String): Boolean =
        TestDatabase.connection().use { connection ->
            connection.prepareStatement("SELECT count(*) FROM studio WHERE id = ?").use { statement ->
                statement.setString(1, studioId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1) > 0
                }
            }
        }

    private fun remainingObjectRows(studioId: String): Int =
        TestDatabase.connection().use { connection ->
            connection.prepareStatement("SELECT count(*) FROM stored_object WHERE studio_id = ?").use { statement ->
                statement.setString(1, studioId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }
}
