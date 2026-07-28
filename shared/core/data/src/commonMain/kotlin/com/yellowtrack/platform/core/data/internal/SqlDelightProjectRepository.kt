package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ProjectRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Project as ProjectRow

internal class SqlDelightProjectRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
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
        database().projectQueries.softDelete(deletedAt = clock.now().toEpochMillis(), id = projectId.value)
    }

    private fun Flow<List<ProjectRow>>.mapRows(): Flow<List<Project>> = map { rows -> rows.map { it.toDomain() } }
}
