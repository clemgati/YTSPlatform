package com.yellowtrack.platform.server.sync

import com.yellowtrack.platform.server.Database
import java.sql.Connection
import java.util.UUID

/** What became of one pushed row. */
enum class PushOutcome {
    /** Stored. Nothing was discarded. */
    Applied,

    /**
     * Stored, and something was discarded to store it — or refused because a tombstone
     * beat it. Either way a `sync_conflict` row now holds both versions.
     */
    Conflicted,

    /** Not stored, and nothing was lost, because the push should not have been made. */
    Rejected,
}

data class PushResult(
    val entityTable: String,
    val entityId: String,
    val outcome: PushOutcome,
    /** The version now on the server, so the device can stop being behind. */
    val version: Int,
    val detail: String? = null,
)

data class PulledChanges(
    val cursor: Long,
    val hasMore: Boolean,
    val rows: Map<String, List<Any?>>,
)

/**
 * Reconciliation, as ADR 0008 decided it.
 *
 * Four rules, and each one is here because the alternative loses work quietly:
 *
 * 1. **The cursor is a server-assigned sequence** (decision 1). Pulling is a single
 *    ordered range scan across every synchronised table, because they all draw from one
 *    sequence — which is what lets a device hold one cursor rather than one per table.
 * 2. **Conflicts are detected with `version`** (decision 2), never by comparing clocks.
 * 3. **They are resolved by arrival, and the loser is kept** (decision 3). The push that
 *    arrives later wins, and the version it displaced is written to `sync_conflict` in
 *    full so the studio can read back what it lost.
 * 4. **Tombstones beat concurrent edits** (decision 4). Deleting is deliberate and far
 *    less likely to be accidental than an edit is to be concurrent, and because deletes
 *    are soft the row is recoverable rather than gone.
 */
class Reconciler(
    private val database: Database,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Everything the studio has that this device has not seen, oldest change first.
     *
     * The three tables are ordered *together* rather than page by page each. Paging them
     * separately and reporting the highest sequence seen would step the cursor past rows
     * in the other tables that sit below it, and those rows would never be pulled again —
     * the exact silent loss the sequence exists to prevent.
     */
    fun pull(
        studioId: String,
        since: Long,
        limit: Int = DEFAULT_PAGE,
    ): PulledChanges =
        database.inStudio(studioId) { connection ->
            val page = changedIds(connection, since, limit)
            val rows =
                SyncedEntity.all.associate { entity ->
                    val ids = page.ids[entity.table].orEmpty()
                    entity.table to if (ids.isEmpty()) emptyList() else fetch(connection, entity, ids)
                }

            PulledChanges(cursor = page.cursor.coerceAtLeast(since), hasMore = page.hasMore, rows = rows)
        }

    /**
     * Applies one pushed entity.
     *
     * Returns rather than throws on a conflict: a drain running after a day offline cannot
     * stop and ask a photographer to resolve fourteen of them, and a rejection the device
     * cannot act on becomes a stuck outbox.
     */
    fun <T> push(
        studioId: String,
        entity: SyncedEntity<T>,
        incoming: T,
    ): PushResult =
        database.inStudio(studioId) { connection ->
            val id = entity.identify(incoming)

            // Checked here rather than left to row level security, which would refuse it
            // as an opaque SQLSTATE. A device pushing another studio's row is a bug worth
            // naming.
            if (entity.studioOf(incoming) != studioId) {
                return@inStudio PushResult(
                    entity.table,
                    id,
                    PushOutcome.Rejected,
                    entity.versionOf(incoming),
                    "that row belongs to another studio",
                )
            }

            // A parent carrying its children is refused rather than quietly stripped: dropping
            // them would leave the device believing it had uploaded something it had not.
            val carriesChildren =
                when {
                    entity is SyncedEntity.Clients ->
                        SyncedEntity.Clients.rejectionReason(
                            incoming as com.yellowtrack.platform.core.model.client.Client,
                        )
                    entity is SyncedEntity.Invoices ->
                        SyncedEntity.Invoices.rejectionReason(
                            incoming as com.yellowtrack.platform.core.model.invoice.Invoice,
                        )
                    else -> null
                }

            carriesChildren?.let { reason ->
                return@inStudio PushResult(
                    entity.table,
                    id,
                    PushOutcome.Rejected,
                    entity.versionOf(incoming),
                    reason,
                )
            }

            val existing = current(connection, entity, id)
            val incomingVersion = entity.versionOf(incoming)

            if (existing == null) {
                entity.upsert(connection, incoming, incomingVersion)
                return@inStudio PushResult(entity.table, id, PushOutcome.Applied, incomingVersion)
            }

            val existingVersion = entity.versionOf(existing)
            val existingIsTombstone = entity.deletedAtOf(existing) != null
            val incomingIsTombstone = entity.deletedAtOf(incoming) != null

            // Rule 4, first half: the row is already deleted and this is an edit. The
            // tombstone stands whatever order they arrived in, so the edit is the loser —
            // and is kept, because a discarded edit is still discarded work.
            if (existingIsTombstone && !incomingIsTombstone) {
                recordConflict(connection, studioId, entity, id, losing = incoming, winning = existing)
                return@inStudio PushResult(
                    entity.table,
                    id,
                    PushOutcome.Conflicted,
                    existingVersion,
                    "that booking was deleted on another device",
                )
            }

            // The row has not moved past what this edit was based on, so nothing is being
            // displaced. Deletes land here too, which is rule 4's second half: a delete
            // arriving over a live row is an ordinary write.
            if (incomingVersion > existingVersion) {
                entity.upsert(connection, incoming, incomingVersion)
                return@inStudio PushResult(entity.table, id, PushOutcome.Applied, incomingVersion)
            }

            // Rules 2 and 3. The server moved on while this device was away, so something
            // has to give; the later arrival wins and the displaced version is preserved.
            //
            // The stored version is one past the higher of the two. Keeping the incoming
            // number would leave two devices sitting on the same version, and every push
            // between them would conflict forever after.
            val settledVersion = maxOf(incomingVersion, existingVersion) + 1
            recordConflict(connection, studioId, entity, id, losing = existing, winning = incoming)
            entity.upsert(connection, incoming, settledVersion)

            PushResult(
                entity.table,
                id,
                PushOutcome.Conflicted,
                settledVersion,
                "this row had also been changed elsewhere; the other version was kept",
            )
        }

    // -- Reading ---------------------------------------------------------------------------

    private data class Page(
        val ids: Map<String, List<String>>,
        val cursor: Long,
        val hasMore: Boolean,
    )

    /**
     * One ordered pass over every synchronised table.
     *
     * Asks for one row more than the page, which is how `hasMore` is answered without a
     * second count query.
     */
    private fun changedIds(
        connection: Connection,
        since: Long,
        limit: Int,
    ): Page {
        val union =
            SyncedEntity.all.joinToString(" UNION ALL ") { entity ->
                "SELECT '${entity.table}' AS entity_table, id, server_seq FROM ${entity.table} WHERE server_seq > ?"
            }

        return connection.prepareStatement("$union ORDER BY server_seq LIMIT ?").use { statement ->
            SyncedEntity.all.indices.forEach { index -> statement.setLong(index + 1, since) }
            statement.setInt(SyncedEntity.all.size + 1, limit + 1)

            statement.executeQuery().use { rows ->
                val found = mutableListOf<Triple<String, String, Long>>()
                while (rows.next()) {
                    found += Triple(rows.getString(1), rows.getString(2), rows.getLong(3))
                }

                val hasMore = found.size > limit
                val page = if (hasMore) found.take(limit) else found

                Page(
                    ids = page.groupBy({ it.first }, { it.second }),
                    cursor = page.lastOrNull()?.third ?: since,
                    hasMore = hasMore,
                )
            }
        }
    }

    private fun <T> fetch(
        connection: Connection,
        entity: SyncedEntity<T>,
        ids: List<String>,
    ): List<T> =
        connection
            .prepareStatement(
                "SELECT * FROM ${entity.table} WHERE id = ANY (?) ORDER BY server_seq",
            ).use { statement ->
                statement.setArray(1, connection.createArrayOf("text", ids.toTypedArray()))
                statement.executeQuery().use { rows ->
                    buildList { while (rows.next()) add(entity.read(rows)) }
                }
            }

    private fun <T> current(
        connection: Connection,
        entity: SyncedEntity<T>,
        id: String,
    ): T? =
        connection.prepareStatement("SELECT * FROM ${entity.table} WHERE id = ?").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { rows -> if (rows.next()) entity.read(rows) else null }
        }

    // -- Keeping the loser -------------------------------------------------------------------

    private fun <T> recordConflict(
        connection: Connection,
        studioId: String,
        entity: SyncedEntity<T>,
        entityId: String,
        losing: T,
        winning: T,
    ) {
        val timestamp = now()

        connection
            .prepareStatement(
                """
                INSERT INTO sync_conflict(id, studio_id, entity_table, entity_id,
                                          losing_payload, winning_payload, detected_at,
                                          created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, UUID.randomUUID().toString())
                statement.setString(2, studioId)
                statement.setString(3, entity.table)
                statement.setString(4, entityId)
                statement.setString(5, entity.encode(losing))
                statement.setString(6, entity.encode(winning))
                statement.setLong(7, timestamp)
                statement.setLong(8, timestamp)
                statement.setLong(9, timestamp)
                statement.executeUpdate()
            }
    }

    companion object {
        /**
         * Bounded so one pull cannot try to hold a studio's whole history in memory. A
         * device behind by more than this makes several round trips, which `hasMore` tells
         * it to do.
         */
        const val DEFAULT_PAGE = 500
    }
}
