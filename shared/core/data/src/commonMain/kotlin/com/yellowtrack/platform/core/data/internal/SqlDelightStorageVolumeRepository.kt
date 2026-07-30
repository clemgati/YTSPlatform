package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.StorageVolumeRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Storage_volume as StorageVolumeRow

internal class SqlDelightStorageVolumeRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    StorageVolumeRepository {
    override fun observeVolumes(): Flow<List<StorageVolume>> =
        observing { db ->
            db.storageVolumeQueries
                .selectAll(studioContext.studioId.value)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override suspend fun getVolume(volumeId: StorageVolumeId): StorageVolume? =
        observing { db ->
            db.storageVolumeQueries
                .selectById(volumeId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun saveVolume(volume: StorageVolume) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.storageVolumeQueries.insertOrIgnore(
                id = volume.id.value,
                studio_id = volume.studioId.value,
                label = volume.label,
                kind = volume.kind.name,
                status = volume.status.name,
                is_offsite = if (volume.isOffsite) 1L else 0L,
                last_checked_at = volume.lastCheckedAt.toEpochMillisOrNull(),
                notes = volume.notes,
                created_at = volume.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = volume.audit.deletedAt.toEpochMillisOrNull(),
                version = volume.audit.version.toLong(),
            )

            db.storageVolumeQueries.update(
                label = volume.label,
                kind = volume.kind.name,
                status = volume.status.name,
                isOffsite = if (volume.isOffsite) 1L else 0L,
                lastCheckedAt = volume.lastCheckedAt.toEpochMillisOrNull(),
                notes = volume.notes,
                updatedAt = now,
                deletedAt = volume.audit.deletedAt.toEpochMillisOrNull(),
                version = volume.audit.version.toLong(),
                id = volume.id.value,
            )
        }
    }

    override suspend fun deleteVolume(volumeId: StorageVolumeId) {
        database().storageVolumeQueries.softDelete(deletedAt = clock.now().toEpochMillis(), id = volumeId.value)
    }

    override fun observeCopiesOnVolume(volumeId: StorageVolumeId): Flow<List<MediaCopy>> =
        observing { db ->
            db.mediaCopyQueries
                .selectByVolume(volumeId.value)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }
}

internal fun StorageVolumeRow.toDomain(): StorageVolume =
    StorageVolume(
        id = StorageVolumeId(id),
        studioId = StudioId(studio_id),
        label = label,
        kind = enumOrDefault(kind, StorageKind.ExternalDrive),
        // An unreadable status reads as in use: wrongly marking a drive failed would tell
        // a studio it has lost copies it still has, and send it chasing nothing.
        status = enumOrDefault(status, VolumeStatus.InUse),
        isOffsite = is_offsite != 0L,
        lastCheckedAt = last_checked_at.toInstantOrNull(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
