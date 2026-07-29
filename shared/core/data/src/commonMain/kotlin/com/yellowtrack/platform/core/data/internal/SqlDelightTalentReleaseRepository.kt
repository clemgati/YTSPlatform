package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.TalentReleaseRepository
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Talent_release as ReleaseRow

/**
 * Releases are reached through their session, which is already scoped to the studio, so
 * this repository takes no `StudioContext` of its own.
 */
internal class SqlDelightTalentReleaseRepository(
    provider: DatabaseProvider,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    TalentReleaseRepository {
    override fun observeReleasesForSession(sessionId: SessionId): Flow<List<TalentRelease>> =
        observing { db ->
            db.talentReleaseQueries
                .selectBySession(sessionId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getRelease(releaseId: TalentReleaseId): TalentRelease? =
        observing { db ->
            db.talentReleaseQueries
                .selectById(releaseId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun saveRelease(release: TalentRelease) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.talentReleaseQueries.insertOrIgnore(
                id = release.id.value,
                studio_id = release.studioId.value,
                session_id = release.sessionId.value,
                person_name = release.personName,
                kind = release.kind.name,
                status = release.status.name,
                signed_at = release.signedAt.toEpochMillisOrNull(),
                guardian_name = release.guardianName,
                email = release.email,
                document_reference = release.documentReference,
                notes = release.notes,
                created_at = release.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = release.audit.deletedAt.toEpochMillisOrNull(),
                version = release.audit.version.toLong(),
            )

            db.talentReleaseQueries.update(
                sessionId = release.sessionId.value,
                personName = release.personName,
                kind = release.kind.name,
                status = release.status.name,
                signedAt = release.signedAt.toEpochMillisOrNull(),
                guardianName = release.guardianName,
                email = release.email,
                documentReference = release.documentReference,
                notes = release.notes,
                updatedAt = now,
                deletedAt = release.audit.deletedAt.toEpochMillisOrNull(),
                version = release.audit.version.toLong(),
                id = release.id.value,
            )
        }
    }

    override suspend fun deleteRelease(releaseId: TalentReleaseId) {
        database().talentReleaseQueries.softDelete(
            deletedAt = clock.now().toEpochMillis(),
            id = releaseId.value,
        )
    }

    private fun Flow<List<ReleaseRow>>.mapRows(): Flow<List<TalentRelease>> = map { rows -> rows.map { it.toDomain() } }
}
