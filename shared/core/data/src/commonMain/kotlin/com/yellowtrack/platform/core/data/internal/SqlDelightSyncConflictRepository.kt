package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.SyncConflictRepository
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Sync_conflict as SyncConflictRow

internal class SqlDelightSyncConflictRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    SyncConflictRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeUnresolved(): Flow<List<SyncConflict>> =
        observing { db ->
            db.syncQueries
                .selectUnresolvedConflicts(studioId)
                .asListFlow(dispatcher)
                .map { rows -> rows.map(SyncConflictRow::toDomain) }
        }

    override fun observeUnresolvedCount(): Flow<Int> =
        observing { db ->
            db.syncQueries
                .countUnresolvedConflicts(studioId)
                .asListFlow(dispatcher)
                .map { counts -> counts.firstOrNull()?.toInt() ?: 0 }
        }

    override suspend fun resolve(id: SyncConflictId) {
        database().syncQueries.markConflictResolved(
            resolvedAt = clock.now().toEpochMillis(),
            id = id.value,
        )
    }
}

internal fun SyncConflictRow.toDomain(): SyncConflict =
    SyncConflict(
        id = SyncConflictId(id),
        studioId = StudioId(studio_id),
        entityTable = entity_table,
        entityId = entity_id,
        losingPayload = losing_payload,
        winningPayload = winning_payload,
        detectedAt = detected_at.toInstant(),
        resolvedAt = resolved_at.toInstantOrNull(),
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
