package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.MediaCopyRepository
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Media_copy as MediaCopyRow

/**
 * Copies are reached through their session, which is already scoped to the studio, so this
 * repository takes no `StudioContext` of its own.
 */
internal class SqlDelightMediaCopyRepository(
    provider: DatabaseProvider,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    MediaCopyRepository {
    override fun observeCopiesForSession(sessionId: SessionId): Flow<List<MediaCopy>> =
        observing { db ->
            db.mediaCopyQueries
                .selectBySession(sessionId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getCopy(copyId: MediaCopyId): MediaCopy? =
        observing { db ->
            db.mediaCopyQueries
                .selectById(copyId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun saveCopy(copy: MediaCopy) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.mediaCopyQueries.insertOrIgnore(
                id = copy.id.value,
                studio_id = copy.studioId.value,
                session_id = copy.sessionId.value,
                volume_id = copy.volumeId?.value,
                volume_name = copy.volumeName,
                path = copy.path,
                verified_file_count = copy.verifiedFileCount?.toLong(),
                verified_bytes = copy.verifiedBytes,
                kind = copy.kind.name,
                is_offsite = if (copy.isOffsite) 1L else 0L,
                copied_at = copy.copiedAt.toEpochMillisOrNull(),
                verified_at = copy.verifiedAt.toEpochMillisOrNull(),
                notes = copy.notes,
                created_at = copy.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = copy.audit.deletedAt.toEpochMillisOrNull(),
                version = copy.audit.version.toLong(),
            )

            db.mediaCopyQueries.update(
                sessionId = copy.sessionId.value,
                volumeId = copy.volumeId?.value,
                volumeName = copy.volumeName,
                path = copy.path,
                verifiedFileCount = copy.verifiedFileCount?.toLong(),
                verifiedBytes = copy.verifiedBytes,
                kind = copy.kind.name,
                isOffsite = if (copy.isOffsite) 1L else 0L,
                copiedAt = copy.copiedAt.toEpochMillisOrNull(),
                verifiedAt = copy.verifiedAt.toEpochMillisOrNull(),
                notes = copy.notes,
                updatedAt = now,
                deletedAt = copy.audit.deletedAt.toEpochMillisOrNull(),
                version = copy.audit.version.toLong(),
                id = copy.id.value,
            )
        }
    }

    override suspend fun deleteCopy(copyId: MediaCopyId) {
        database().mediaCopyQueries.softDelete(deletedAt = clock.now().toEpochMillis(), id = copyId.value)
    }

    private fun Flow<List<MediaCopyRow>>.mapRows(): Flow<List<MediaCopy>> = map { rows -> rows.map { it.toDomain() } }
}
