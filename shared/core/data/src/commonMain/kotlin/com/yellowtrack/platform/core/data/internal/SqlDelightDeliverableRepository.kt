package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.DeliverableRepository
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yellowtrack.platform.core.database.Deliverable as DeliverableRow

/**
 * Deliverables are reached through their booking, which is already scoped to the studio,
 * so this repository takes no `StudioContext` of its own.
 */
internal class SqlDelightDeliverableRepository(
    provider: DatabaseProvider,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    DeliverableRepository {
    override fun observeDeliverablesForProject(projectId: ProjectId): Flow<List<Deliverable>> =
        observing { db ->
            db.deliverableQueries
                .selectByProject(projectId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getDeliverable(deliverableId: DeliverableId): Deliverable? =
        observing { db ->
            db.deliverableQueries
                .selectById(deliverableId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun saveDeliverable(deliverable: Deliverable) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.deliverableQueries.insertOrIgnore(
                id = deliverable.id.value,
                studio_id = deliverable.studioId.value,
                project_id = deliverable.projectId.value,
                name = deliverable.name,
                kind = deliverable.kind.name,
                status = deliverable.status.name,
                due_at = deliverable.dueAt.toEpochMillisOrNull(),
                delivered_at = deliverable.deliveredAt.toEpochMillisOrNull(),
                approved_at = deliverable.approvedAt.toEpochMillisOrNull(),
                revisions_used = deliverable.revisionsUsed.toLong(),
                notes = deliverable.notes,
                created_at = deliverable.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = deliverable.audit.deletedAt.toEpochMillisOrNull(),
                version = deliverable.audit.version.toLong(),
            )

            db.deliverableQueries.update(
                projectId = deliverable.projectId.value,
                name = deliverable.name,
                kind = deliverable.kind.name,
                status = deliverable.status.name,
                dueAt = deliverable.dueAt.toEpochMillisOrNull(),
                deliveredAt = deliverable.deliveredAt.toEpochMillisOrNull(),
                approvedAt = deliverable.approvedAt.toEpochMillisOrNull(),
                revisionsUsed = deliverable.revisionsUsed.toLong(),
                notes = deliverable.notes,
                updatedAt = now,
                deletedAt = deliverable.audit.deletedAt.toEpochMillisOrNull(),
                version = deliverable.audit.version.toLong(),
                id = deliverable.id.value,
            )
        }
    }

    override suspend fun deleteDeliverable(deliverableId: DeliverableId) {
        database().deliverableQueries.softDelete(
            deletedAt = clock.now().toEpochMillis(),
            id = deliverableId.value,
        )
    }

    private fun Flow<List<DeliverableRow>>.mapRows(): Flow<List<Deliverable>> =
        map { rows -> rows.map { it.toDomain() } }
}
