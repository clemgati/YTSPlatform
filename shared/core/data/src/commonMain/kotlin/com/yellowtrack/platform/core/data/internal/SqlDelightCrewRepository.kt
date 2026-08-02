package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.CrewRepository
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Crew_member as CrewRow

/**
 * Crew are reached through their session, which is already scoped to the studio, so this
 * repository takes no `StudioContext` of its own.
 */
internal class SqlDelightCrewRepository(
    provider: DatabaseProvider,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    CrewRepository {
    override fun observeCrewForSession(sessionId: SessionId): Flow<List<CrewMember>> =
        observing { db ->
            db.crewMemberQueries
                .selectBySession(sessionId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getCrewMember(crewMemberId: CrewMemberId): CrewMember? =
        observing { db ->
            db.crewMemberQueries
                .selectById(crewMemberId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun saveCrewMember(crewMember: CrewMember) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.crewMemberQueries.insertOrIgnore(
                id = crewMember.id.value,
                studio_id = crewMember.studioId.value,
                session_id = crewMember.sessionId.value,
                name = crewMember.name,
                role = crewMember.role.name,
                phone = crewMember.phone,
                call_time = crewMember.callTime.toEpochMillisOrNull(),
                notes = crewMember.notes,
                created_at = crewMember.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = crewMember.audit.deletedAt.toEpochMillisOrNull(),
                version = crewMember.audit.version.toLong(),
            )

            db.crewMemberQueries.update(
                sessionId = crewMember.sessionId.value,
                name = crewMember.name,
                role = crewMember.role.name,
                phone = crewMember.phone,
                callTime = crewMember.callTime.toEpochMillisOrNull(),
                notes = crewMember.notes,
                updatedAt = now,
                deletedAt = crewMember.audit.deletedAt.toEpochMillisOrNull(),
                version = crewMember.audit.version.toLong(),
                id = crewMember.id.value,
            )

            db.enqueueForSync(
                crewMember.studioId.value,
                SyncTables.CREW_MEMBER,
                crewMember.id.value,
                OutboxOperation.Upsert,
                now,
            )
        }
    }

    override suspend fun deleteCrewMember(crewMemberId: CrewMemberId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // The studio comes from the row rather than from a context: these are reached
        // through their parent, which is already scoped, so this repository never held one.
        val studio =
            db.crewMemberQueries
                .selectByIdForSync(crewMemberId.value)
                .awaitAsOneOrNull()
                ?.studio_id ?: return

        db.transaction {
            db.crewMemberQueries.softDelete(deletedAt = now, id = crewMemberId.value)
            db.enqueueForSync(studio, SyncTables.CREW_MEMBER, crewMemberId.value, OutboxOperation.Delete, now)
        }
    }

    private fun Flow<List<CrewRow>>.mapRows(): Flow<List<CrewMember>> = map { rows -> rows.map { it.toDomain() } }
}
