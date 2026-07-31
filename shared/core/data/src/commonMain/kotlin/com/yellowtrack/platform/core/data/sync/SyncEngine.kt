package com.yellowtrack.platform.core.data.sync

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ClientRepository
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.internal.SyncTables
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.database.YellowTrackDatabase
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.SessionId

/** What one reconciliation did. */
data class SyncReport(
    val uploaded: Int,
    val downloaded: Int,
    /** Rows the server took, having discarded a version to do it. */
    val conflicted: Int,
    val rejected: Int,
    val cursor: Long,
) {
    val isQuiet: Boolean get() = uploaded == 0 && downloaded == 0
}

/**
 * The device half of reconciliation.
 *
 * Drains the outbox, applies what comes back, and remembers how far it has got. Everything
 * it does is designed around one fact from `docs/adr/0008-synchronisation-semantics.md`:
 * this is the only part of the application whose bugs are invisible. A row that fails to
 * upload does not throw, a row that fails to arrive is simply absent, and neither is
 * noticed on the device where the work was done.
 *
 * ## Push before pull
 *
 * Uploading first means a conflict is detected while this device still holds the version
 * that will lose, so the server can keep it. Pulling first would overwrite that version
 * locally and then upload the row the server already had, and the studio's work would be
 * gone before anything noticed it was in danger.
 */
class SyncEngine(
    private val provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val transport: SyncTransport,
    private val clients: ClientRepository,
    private val projects: ProjectRepository,
    private val sessions: SessionRepository,
    private val clock: AppClock,
) {
    private val studioId get() = studioContext.studioId.value

    suspend fun sync(): SyncReport {
        val database = provider.database()

        val pushed = drain(database)
        val pulled = apply(database)

        return SyncReport(
            uploaded = pushed.uploaded,
            downloaded = pulled.downloaded,
            conflicted = pushed.conflicted,
            rejected = pushed.rejected,
            cursor = pulled.cursor,
        )
    }

    // -- Uploading -----------------------------------------------------------------------

    private data class PushSummary(
        val uploaded: Int,
        val conflicted: Int,
        val rejected: Int,
    )

    /**
     * Uploads what the outbox says has changed.
     *
     * Entries are collapsed by identity first. Three edits to one booking queue three
     * entries and there is one row to send, so sending it three times would be three
     * chances to conflict with the other device over work that is already superseded.
     *
     * Rows are then **re-read** rather than taken from the queue (ADR 0008 decision 6). The
     * consequence is worth knowing before debugging it: an entity deleted after its entry
     * was queued uploads as a tombstone, not as its last live state. That is correct, and
     * it is surprising the first time.
     */
    private suspend fun drain(database: YellowTrackDatabase): PushSummary {
        val pending = database.outboxQueries.selectPending(studioId, BATCH.toLong()).awaitAsList()
        if (pending.isEmpty()) return PushSummary(0, 0, 0)

        val wanted = pending.map { it.entity_table to it.entity_id }.distinct()

        val changes =
            ChangesToPush(
                clients = wanted.forTable(SyncTables.CLIENT).mapNotNull { clients.getClient(ClientId(it)) },
                projects = wanted.forTable(SyncTables.PROJECT).mapNotNull { projects.getProject(ProjectId(it)) },
                sessions = wanted.forTable(SyncTables.SESSION).mapNotNull { sessions.getSession(SessionId(it)) },
            )

        // A queued row that no longer exists at all — never uploaded, then hard-deleted —
        // has nothing to send and nothing to keep asking about.
        val vanished = wanted - changes.identities()
        val acks = if (changes.isEmpty) emptyList() else transport.push(changes)

        val byIdentity = acks.associateBy { it.entityTable to it.entityId }

        database.transaction {
            pending.forEach { entry ->
                val identity = entry.entity_table to entry.entity_id
                when (
                    byIdentity[identity]?.outcome
                        ?: if (identity in vanished) PushOutcome.Applied else PushOutcome.Unanswered
                ) {
                    // Conflicted still means stored: the server took this version and kept
                    // the one it displaced. The entry has done its job.
                    PushOutcome.Applied, PushOutcome.Conflicted -> database.outboxQueries.delete(entry.id)

                    // Kept, both of them. A rejection is a bug to look at, and an
                    // unanswered push may simply be a connection that dropped mid-flight —
                    // discarding either would discard the studio's work.
                    PushOutcome.Rejected ->
                        database.outboxQueries.recordFailure(byIdentity[identity]?.detail, entry.id)

                    PushOutcome.Unanswered ->
                        database.outboxQueries.recordFailure("the server did not answer about this row", entry.id)
                }
            }
        }

        return PushSummary(
            uploaded = acks.count { it.outcome == PushOutcome.Applied || it.outcome == PushOutcome.Conflicted },
            conflicted = acks.count { it.outcome == PushOutcome.Conflicted },
            rejected = acks.count { it.outcome == PushOutcome.Rejected },
        )
    }

    // -- Downloading ---------------------------------------------------------------------

    private data class PullSummary(
        val downloaded: Int,
        val cursor: Long,
    )

    /**
     * Applies everything past the cursor, a page at a time.
     *
     * The cursor is only advanced **after** the page it describes has been written. A
     * cursor saved first and rows written second would, on a crash in between, skip those
     * rows permanently — they are past the cursor and will never be offered again.
     *
     * Rows are written straight to the tables rather than through the repositories,
     * because the repositories enqueue to the outbox. Applying a pulled row through them
     * would queue it straight back for upload, and the two devices would push the same row
     * at each other indefinitely.
     */
    private suspend fun apply(database: YellowTrackDatabase): PullSummary {
        var cursor = currentCursor(database)
        var downloaded = 0
        var pages = 0

        while (pages < MAX_PAGES) {
            val page = transport.pull(since = cursor, limit = BATCH)
            val arrived = page.clients.size + page.projects.size + page.sessions.size + page.conflicts.size

            database.transaction {
                page.clients.forEach { database.applyClient(it) }
                page.projects.forEach { database.applyProject(it) }
                page.sessions.forEach { database.applySession(it) }
                page.conflicts.forEach { database.applyConflict(it) }

                database.syncQueries.rememberCursor(studioId, page.cursor, clock.now().toEpochMilliseconds())
            }

            downloaded += arrived
            cursor = page.cursor
            pages++

            if (!page.hasMore) break
        }

        return PullSummary(downloaded, cursor)
    }

    private suspend fun currentCursor(database: YellowTrackDatabase): Long =
        database.syncQueries
            .selectCursor(studioId)
            .awaitAsOneOrNull()
            ?.last_server_seq
            ?: 0L

    private fun List<Pair<String, String>>.forTable(table: String) = filter { it.first == table }.map { it.second }

    private fun ChangesToPush.identities(): Set<Pair<String, String>> =
        buildSet {
            clients.forEach { add(SyncTables.CLIENT to it.id.value) }
            projects.forEach { add(SyncTables.PROJECT to it.id.value) }
            sessions.forEach { add(SyncTables.SESSION to it.id.value) }
        }

    private companion object {
        /** One page, and one drain. Small enough that a phone on a bad connection finishes it. */
        const val BATCH = 200

        /**
         * A backstop, not a limit anyone should reach. Pulling stops when the server says
         * there is no more; this is what stops a server that always says otherwise from
         * spinning here forever.
         */
        const val MAX_PAGES = 500
    }
}
