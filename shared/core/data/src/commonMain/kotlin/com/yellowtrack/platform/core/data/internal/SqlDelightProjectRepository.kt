package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.yellowtrack.platform.core.database.Project as ProjectRow

internal class SqlDelightProjectRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
    private val remote: RemoteWriter,
) : DatabaseBackedRepository(provider),
    ProjectRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeProjects(): Flow<List<Project>> =
        observing { db ->
            db.projectQueries
                .selectAll(studioId)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override fun observeProject(projectId: ProjectId): Flow<Project?> =
        observing { db ->
            db.projectQueries
                .selectById(projectId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }

    override fun observeProjectsForClient(clientId: ClientId): Flow<List<Project>> =
        observing { db ->
            db.projectQueries
                .selectByClient(clientId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getProject(projectId: ProjectId): Project? = observeProject(projectId).first()

    override suspend fun saveProject(project: Project) {
        val db = database()
        val now = clock.now().toEpochMillis()

        remote.write(SyncPushRequest(projects = listOf(project)))

        db.transaction {
            db.projectQueries.insertOrIgnore(
                id = project.id.value,
                studio_id = project.studioId.value,
                client_id = project.clientId.value,
                name = project.name,
                service_line = project.serviceLine.name,
                status = project.status.name,
                service_template_id = project.serviceTemplateId?.value,
                contract_value_minor = project.contractValue?.minorUnits,
                contract_currency = project.contractValue?.currency?.code,
                enquired_at = project.enquiredAt.toEpochMillisOrNull(),
                booked_at = project.bookedAt.toEpochMillisOrNull(),
                notes = project.notes,
                created_at = project.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = project.audit.deletedAt.toEpochMillisOrNull(),
                version = project.audit.version.toLong(),
            )

            db.projectQueries.update(
                clientId = project.clientId.value,
                name = project.name,
                serviceLine = project.serviceLine.name,
                status = project.status.name,
                serviceTemplateId = project.serviceTemplateId?.value,
                contractValueMinor = project.contractValue?.minorUnits,
                contractCurrency = project.contractValue?.currency?.code,
                enquiredAt = project.enquiredAt.toEpochMillisOrNull(),
                bookedAt = project.bookedAt.toEpochMillisOrNull(),
                notes = project.notes,
                updatedAt = now,
                deletedAt = project.audit.deletedAt.toEpochMillisOrNull(),
                version = project.audit.version.toLong(),
                id = project.id.value,
            )
        }
    }

    override suspend fun deleteProject(projectId: ProjectId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Wrapped, so the tombstone and the note to upload it cannot be written apart.
        // A delete travels as the row carrying a tombstone, so it is read first.
        val existing = getProject(projectId) ?: return

        remote.write(
            SyncPushRequest(projects = listOf(existing.copy(audit = existing.audit.deleted(instant(now))))),
        )

        db.transaction {
            db.projectQueries.softDelete(deletedAt = now, id = projectId.value)
        }
    }

    private fun Flow<List<ProjectRow>>.mapRows(): Flow<List<Project>> = map { rows -> rows.map { it.toDomain() } }

    private fun instant(millis: Long) = Instant.fromEpochMilliseconds(millis)
}
