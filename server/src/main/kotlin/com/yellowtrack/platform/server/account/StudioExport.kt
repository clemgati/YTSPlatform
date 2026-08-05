package com.yellowtrack.platform.server.account

import com.yellowtrack.platform.server.Database
import com.yellowtrack.platform.server.sync.SyncedEntity
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.sql.Connection

/**
 * Everything one studio has, as a file it can keep.
 *
 * A studio that cannot get its work out of an application is a studio that cannot leave it,
 * and "you may not have your data" is a worse thing to find out than most faults. It is also
 * the half of deletion that has to exist first: offering to erase a business's records
 * without offering it a copy is not a choice anybody should be asked to make.
 *
 * ## Why it reuses the sync entities
 *
 * Each [SyncedEntity] already knows how to read its table and serialise a row — that is how
 * both sides of a discarded edit get kept in `sync_conflict`. Reusing it means an export
 * cannot describe a record differently from the way the application already does, and
 * `SyncFieldCoverageTest` goes on holding every field of every entity covered. A second
 * hand-written serialiser would drift from the first the day somebody adds a column, and
 * would drift *silently*, which is the shape of failure worth designing out.
 *
 * ## Why there is no studio_id in the query
 *
 * The rows are read inside [Database.inStudio], so the row level security policies do the
 * scoping. A `WHERE studio_id = ?` would work today and would be one edit away from not
 * working — and an export that quietly included another studio's clients is about the worst
 * single bug this application could have. Postgres refusing to return the rows at all is a
 * stronger guarantee than a clause being correct, and `RowLevelSecurityTest` already breaks
 * it deliberately to prove it holds.
 */
class StudioExport(
    private val database: Database,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Reads the whole studio.
     *
     * Held in memory rather than streamed. A studio with a decade of work is a few megabytes
     * of text — the photographs themselves have never been in this database — so the
     * complexity of streaming would buy nothing a `t4g.small` notices.
     */
    fun of(
        studioId: String,
        accountId: String,
    ): JsonObject =
        database.inStudio(studioId) { connection ->
            buildJsonObject {
                put("exportedAt", now())
                put("studioId", studioId)
                // Named so a file found on a disk two years from now identifies itself.
                put("application", "Yellow Track")
                put("format", FORMAT_VERSION)

                put("account", accountOf(connection, accountId))
                put("studio", studioOf(connection, studioId))

                put(
                    "records",
                    buildJsonObject {
                        // Every entity, including any that is empty. An absent key and an
                        // empty list read the same to a person and differently to a program,
                        // and "your leads are missing" is not a question worth having.
                        SyncedEntity.all.forEach { entity ->
                            put(entity.table, rowsOf(connection, entity))
                        }
                    },
                )
            }
        }

    private fun <T> rowsOf(
        connection: Connection,
        entity: SyncedEntity<T>,
    ) = buildJsonArray {
        // No ORDER BY beyond the primary key: an export is a set of records rather than a
        // sequence, and a stable order makes two exports of unchanged data compare equal,
        // which is what makes "has anything changed since I last kept a copy" answerable.
        connection.prepareStatement("SELECT * FROM ${entity.table} ORDER BY id").use { statement ->
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    add(rawJson(entity.encode(entity.read(rows))))
                }
            }
        }
    }

    private fun accountOf(
        connection: Connection,
        accountId: String,
    ): JsonObject =
        connection
            .prepareStatement("SELECT email, name, created_at FROM account WHERE id = ?")
            .use { statement ->
                statement.setString(1, accountId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) {
                        JsonObject(emptyMap())
                    } else {
                        buildJsonObject {
                            put("email", rows.getString("email"))
                            put("name", rows.getString("name"))
                            put("createdAt", rows.getLong("created_at"))
                            // password_hash is deliberately absent. It is not the studio's
                            // data in any useful sense, and a file people email to
                            // themselves is the last place it should exist.
                        }
                    }
                }
            }

    private fun studioOf(
        connection: Connection,
        studioId: String,
    ): JsonObject =
        connection
            .prepareStatement("SELECT name, created_at FROM studio WHERE id = ?")
            .use { statement ->
                statement.setString(1, studioId)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) {
                        JsonObject(emptyMap())
                    } else {
                        buildJsonObject {
                            put("name", rows.getString("name"))
                            put("createdAt", rows.getLong("created_at"))
                        }
                    }
                }
            }

    /**
     * Puts an already-serialised record into the document as JSON rather than as a string.
     *
     * [SyncedEntity.encode] returns text because `sync_conflict` stores text. Nesting that
     * verbatim would produce a file where every record is one long escaped string — readable
     * by nothing, and technically an export.
     */
    private fun rawJson(encoded: String): JsonElement =
        runCatching {
            kotlinx.serialization.json.Json
                .parseToJsonElement(encoded)
        }
            // Kept rather than dropped. A record that will not parse is still the studio's,
            // and losing it silently from a file somebody asked for is worse than an odd
            // entry they can ask about.
            .getOrElse { JsonPrimitive(encoded) }

    private companion object {
        /**
         * Bumped when the shape changes in a way something reading an old file would notice.
         * Present from the first version so there is never a file that predates the idea.
         */
        const val FORMAT_VERSION = 1
    }
}
