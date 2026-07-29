package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ShotRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Shot as ShotRow

/**
 * Shots are reached through their session, which is already scoped to the studio, so this
 * repository takes no [StudioContext] of its own — every query is bounded by a session id
 * that could only have come from a studio-scoped list.
 */
internal class SqlDelightShotRepository(
    provider: DatabaseProvider,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    ShotRepository {
    override fun observeShotsForSession(sessionId: SessionId): Flow<List<Shot>> =
        observing { db ->
            db.shotQueries
                .selectBySession(sessionId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getShot(shotId: ShotId): Shot? =
        observing { db ->
            db.shotQueries
                .selectById(shotId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun saveShot(shot: Shot) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.shotQueries.insertOrIgnore(
                id = shot.id.value,
                studio_id = shot.studioId.value,
                session_id = shot.sessionId.value,
                description = shot.description,
                group_name = shot.group,
                people = shot.people,
                position = shot.position.toLong(),
                is_captured = if (shot.isCaptured) 1L else 0L,
                captured_at = shot.capturedAt.toEpochMillisOrNull(),
                notes = shot.notes,
                created_at = shot.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = shot.audit.deletedAt.toEpochMillisOrNull(),
                version = shot.audit.version.toLong(),
            )

            db.shotQueries.update(
                sessionId = shot.sessionId.value,
                description = shot.description,
                groupName = shot.group,
                people = shot.people,
                position = shot.position.toLong(),
                isCaptured = if (shot.isCaptured) 1L else 0L,
                capturedAt = shot.capturedAt.toEpochMillisOrNull(),
                notes = shot.notes,
                updatedAt = now,
                deletedAt = shot.audit.deletedAt.toEpochMillisOrNull(),
                version = shot.audit.version.toLong(),
                id = shot.id.value,
            )
        }
    }

    override suspend fun deleteShot(shotId: ShotId) {
        database().shotQueries.softDelete(deletedAt = clock.now().toEpochMillis(), id = shotId.value)
    }

    private fun Flow<List<ShotRow>>.mapRows(): Flow<List<Shot>> = map { rows -> rows.map { it.toDomain() } }
}
