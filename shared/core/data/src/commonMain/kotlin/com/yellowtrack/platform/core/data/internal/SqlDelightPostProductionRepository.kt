package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.PostProductionRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Post_task as PostTaskRow

internal class SqlDelightPostProductionRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    PostProductionRepository {
    override fun observeTasksForProject(projectId: ProjectId): Flow<List<PostProductionTask>> =
        observing { db ->
            db.postTaskQueries
                .selectByProject(projectId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override fun observeCompletedTasks(): Flow<List<PostProductionTask>> =
        observing { db ->
            db.postTaskQueries
                .selectCompleted(studioContext.studioId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getTask(taskId: PostProductionTaskId): PostProductionTask? =
        observing { db ->
            db.postTaskQueries
                .selectById(taskId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun saveTask(task: PostProductionTask) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.postTaskQueries.insertOrIgnore(
                id = task.id.value,
                studio_id = task.studioId.value,
                project_id = task.projectId.value,
                name = task.name,
                kind = task.kind.name,
                status = task.status.name,
                estimated_hours = task.estimatedHours,
                actual_hours = task.actualHours,
                completed_at = task.completedAt.toEpochMillisOrNull(),
                notes = task.notes,
                created_at = task.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = task.audit.deletedAt.toEpochMillisOrNull(),
                version = task.audit.version.toLong(),
            )

            db.postTaskQueries.update(
                projectId = task.projectId.value,
                name = task.name,
                kind = task.kind.name,
                status = task.status.name,
                estimatedHours = task.estimatedHours,
                actualHours = task.actualHours,
                completedAt = task.completedAt.toEpochMillisOrNull(),
                notes = task.notes,
                updatedAt = now,
                deletedAt = task.audit.deletedAt.toEpochMillisOrNull(),
                version = task.audit.version.toLong(),
                id = task.id.value,
            )

            db.enqueueForSync(task.studioId.value, SyncTables.POST_TASK, task.id.value, OutboxOperation.Upsert, now)
        }
    }

    override suspend fun deleteTask(taskId: PostProductionTaskId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Taken from the row: this repository reaches its rows through a parent, so it holds
        // no studio of its own.
        val studio =
            db.postTaskQueries
                .selectByIdForSync(taskId.value)
                .awaitAsOneOrNull()
                ?.studio_id ?: return

        db.transaction {
            db.postTaskQueries.softDelete(deletedAt = now, id = taskId.value)

            db.enqueueForSync(studio, SyncTables.POST_TASK, taskId.value, OutboxOperation.Delete, now)
        }
    }

    private fun Flow<List<PostTaskRow>>.mapRows(): Flow<List<PostProductionTask>> =
        map { rows -> rows.map { it.toDomain() } }
}
