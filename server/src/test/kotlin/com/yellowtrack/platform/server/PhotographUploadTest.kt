package com.yellowtrack.platform.server

import com.yellowtrack.platform.server.storage.ObjectStore
import com.yellowtrack.platform.server.storage.StoredObjects
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * What a photograph leaves behind, and what a failure leaves behind.
 *
 * The ordering here is the whole test. `stored_object` is the only record of which keys
 * belong to whom, so an object written to the bucket with no row is an orphan the purge can
 * never find — the exact thing that table exists to prevent. A row with no object is
 * harmless by comparison: nothing reads it, and deleting an absent key is a success as far
 * as S3 is concerned.
 *
 * So the row goes first, and this holds that it does.
 */
class PhotographUploadTest {
    private class RecordingStore(
        private val refuse: Boolean = false,
    ) : ObjectStore {
        val stored = mutableMapOf<String, ByteArray>()

        override fun put(
            key: String,
            contentType: String,
            bytes: ByteArray,
        ) {
            if (refuse) throw IllegalStateException("the bucket is unreachable")
            stored[key] = bytes
        }

        override fun temporaryUrl(
            key: String,
            validFor: Duration,
        ): String = "https://example.invalid/$key"

        override fun delete(keys: List<String>): Set<String> {
            keys.forEach { stored.remove(it) }

            return keys.toSet()
        }
    }

    @Test
    fun `storing a photograph leaves both the object and the row that names it`() {
        val studio = studio()
        val store = RecordingStore()
        val objects = StoredObjects(TestDatabase.database, store)

        val id = objects.store(studio, "image/jpeg", byteArrayOf(1, 2, 3))

        assertEquals(1, store.stored.size, "the bytes should have reached the bucket")
        assertEquals(1, rowsFor(studio), "and the row that makes them findable")
        assertTrue(id.isNotBlank())
    }

    /**
     * The failure that must not leave an orphan.
     *
     * When the bucket refuses, the row is removed again — so a studio that could not store a
     * photograph is left with nothing rather than with a record of something it does not
     * have.
     */
    @Test
    fun `a refused upload leaves no row behind`() {
        val studio = studio()
        val objects = StoredObjects(TestDatabase.database, RecordingStore(refuse = true))

        assertFailsWith<IllegalStateException> {
            objects.store(studio, "image/jpeg", byteArrayOf(1, 2, 3))
        }

        assertEquals(0, rowsFor(studio), "the row was written before the put and must not survive it failing")
    }

    /**
     * The order, stated as a property rather than as a comment.
     *
     * If the bytes went first, a crash between the two would leave an object in the bucket
     * that nothing knows about. The store below records what it was asked to do and when,
     * and the row must already exist by the time it is asked.
     */
    @Test
    fun `the row exists before the bytes are sent`() {
        val studio = studio()
        var rowsWhenAsked = -1

        val watchful =
            object : ObjectStore {
                override fun put(
                    key: String,
                    contentType: String,
                    bytes: ByteArray,
                ) {
                    // The *first* time the bucket is asked, not the last. Recording only the
                    // latest call made this test pass with the order reversed, because a
                    // second put after the row overwrote what the first one had seen — a
                    // test that held nothing while appearing to hold the property.
                    if (rowsWhenAsked < 0) rowsWhenAsked = rowsFor(studio)
                }

                override fun temporaryUrl(
                    key: String,
                    validFor: Duration,
                ): String = ""

                override fun delete(keys: List<String>): Set<String> = keys.toSet()
            }

        StoredObjects(TestDatabase.database, watchful).store(studio, "image/jpeg", byteArrayOf(9))

        assertEquals(
            1,
            rowsWhenAsked,
            "the bucket was asked to store bytes before anything recorded that they would exist",
        )
    }

    // -- Fixtures --------------------------------------------------------------------------

    private fun studio(): String {
        val id = "studio-${UUID.randomUUID()}"

        TestDatabase.connection().use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO studio(id, name, created_at, updated_at, version) VALUES (?, ?, 0, 0, 1)",
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, "Upload fixture")
                    statement.executeUpdate()
                }
        }

        return id
    }

    private fun rowsFor(studioId: String): Int =
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
