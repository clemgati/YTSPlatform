package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import kotlinx.coroutines.flow.Flow

/**
 * Work that reconciliation discarded.
 *
 * `docs/adr/0008-synchronisation-semantics.md` decision 3 made this the condition on which
 * last-write-wins was acceptable at all: the studio is told it happened and can read what
 * the other device said. A conflict table nobody reads is worse than none, because it
 * implies a safety property it is not delivering.
 */
interface SyncConflictRepository {
    /** Oldest first, so a studio meets them in the order they happened. */
    fun observeUnresolved(): Flow<List<SyncConflict>>

    fun observeUnresolvedCount(): Flow<Int>

    /**
     * Marks one as dealt with.
     *
     * The row stays. What was discarded is a fact about the studio's history, and a
     * resolved conflict that vanished would be a question nobody could re-ask.
     */
    suspend fun resolve(id: SyncConflictId)
}
