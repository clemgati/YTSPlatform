package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.yellowtrack.platform.core.database.Session as SessionRow

internal class SqlDelightSessionRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    SessionRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeSessions(): Flow<List<Session>> =
        observing { db ->
            db.sessionQueries
                .selectAll(studioId)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override fun observeSession(sessionId: SessionId): Flow<Session?> =
        observing { db ->
            db.sessionQueries
                .selectById(sessionId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }

    override fun observeSessionsForProject(projectId: ProjectId): Flow<List<Session>> =
        observing { db ->
            db.sessionQueries
                .selectByProject(projectId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override fun observeSessionsBetween(
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Flow<List<Session>> =
        observing { db ->
            db.sessionQueries
                .selectBetween(
                    studioId = studioId,
                    fromInclusive = fromInclusive.toEpochMillis(),
                    toExclusive = toExclusive.toEpochMillis(),
                ).asListFlow(dispatcher)
                .mapRows()
        }

    override fun observeUpcomingSessions(
        from: Instant,
        limit: Int,
    ): Flow<List<Session>> =
        observing { db ->
            db.sessionQueries
                .selectUpcoming(
                    studioId = studioId,
                    fromInclusive = from.toEpochMillis(),
                    limit = limit.toLong(),
                ).asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getSession(sessionId: SessionId): Session? = observeSession(sessionId).first()

    override suspend fun saveSession(session: Session) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.sessionQueries.insertOrIgnore(
                id = session.id.value,
                studio_id = session.studioId.value,
                project_id = session.projectId.value,
                title = session.title,
                kind = session.kind.name,
                status = session.status.name,
                starts_at = session.startsAt.toEpochMillis(),
                ends_at = session.endsAt.toEpochMillis(),
                time_zone_id = session.timeZoneId,
                location_name = session.locationName,
                location_address = session.locationAddress,
                call_time = session.callTime.toEpochMillisOrNull(),
                notes = session.notes,
                created_at = session.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = session.audit.deletedAt.toEpochMillisOrNull(),
                version = session.audit.version.toLong(),
                latitude = session.coordinates?.latitude,
                longitude = session.coordinates?.longitude,
            )

            db.sessionQueries.update(
                projectId = session.projectId.value,
                title = session.title,
                kind = session.kind.name,
                status = session.status.name,
                startsAt = session.startsAt.toEpochMillis(),
                endsAt = session.endsAt.toEpochMillis(),
                timeZoneId = session.timeZoneId,
                locationName = session.locationName,
                locationAddress = session.locationAddress,
                latitude = session.coordinates?.latitude,
                longitude = session.coordinates?.longitude,
                callTime = session.callTime.toEpochMillisOrNull(),
                notes = session.notes,
                updatedAt = now,
                deletedAt = session.audit.deletedAt.toEpochMillisOrNull(),
                version = session.audit.version.toLong(),
                id = session.id.value,
            )

            db.enqueueForSync(session.studioId.value, SyncTables.SESSION, session.id.value, OutboxOperation.Upsert, now)
        }
    }

    override suspend fun deleteSession(sessionId: SessionId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Wrapped, so the tombstone and the note to upload it cannot be written apart.
        db.transaction {
            db.sessionQueries.softDelete(deletedAt = now, id = sessionId.value)
            db.enqueueForSync(studioId, SyncTables.SESSION, sessionId.value, OutboxOperation.Delete, now)
        }
    }

    private fun Flow<List<SessionRow>>.mapRows(): Flow<List<Session>> = map { rows -> rows.map { it.toDomain() } }
}
