package com.yellowtrack.platform.server.storage

import com.yellowtrack.platform.server.Database
import java.util.UUID

/**
 * The register of what a studio has in the bucket.
 *
 * Separate from [ObjectStore] because the two answer different questions and fail
 * differently: one talks to S3, this one is the record that makes the purge possible. A
 * photograph is in the bucket and in this table, and the order those two happen in decides
 * what a crash leaves behind.
 */
class StoredObjects(
    private val database: Database,
    private val store: ObjectStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /**
     * Records the object, then stores the bytes — in that order, deliberately.
     *
     * The two can only fail apart, so the question is which leftover is survivable.
     *
     * A **row without an object** is harmless: nothing can read it, and the purge asks S3 to
     * delete a key that is not there, which S3 treats as success. An **object without a row**
     * is the one that matters — `stored_object` is the only record of which keys belong to
     * whom, so an orphan sits in the bucket forever with nothing that could ever find it.
     * That is precisely what this table exists to prevent, so the row goes first.
     *
     * When the store refuses, the row is removed again rather than left behind. The crash
     * case still leaves one, and that is the survivable half by design.
     */
    fun store(
        studioId: String,
        contentType: String,
        bytes: ByteArray,
        key: String = "$studioId/${newId()}",
    ): String {
        val id = newId()

        database.inStudio(studioId) { connection ->
            connection
                .prepareStatement(
                    """
                    INSERT INTO stored_object(id, studio_id, object_key, content_type, size_bytes, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, studioId)
                    statement.setString(3, key)
                    statement.setString(4, contentType)
                    statement.setLong(5, bytes.size.toLong())
                    statement.setLong(6, now())
                    statement.executeUpdate()
                }
        }

        runCatching { store.put(key, contentType, bytes) }
            .onFailure { failure ->
                database.inStudio(studioId) { connection ->
                    connection.prepareStatement("DELETE FROM stored_object WHERE id = ?").use { statement ->
                        statement.setString(1, id)
                        statement.executeUpdate()
                    }
                }
                throw failure
            }

        return id
    }
}
