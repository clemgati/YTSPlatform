package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ServiceTemplateRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class SqlDelightServiceTemplateRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    ServiceTemplateRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeTemplates(): Flow<List<ServiceTemplate>> =
        observing { db ->
            db.serviceTemplateQueries
                .selectAll(studioId)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override suspend fun getTemplate(id: ServiceTemplateId): ServiceTemplate? =
        database()
            .serviceTemplateQueries
            .selectById(id.value)
            .asOneOrNullFlow(dispatcher)
            .first()
            ?.toDomain()

    override suspend fun saveTemplate(template: ServiceTemplate) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.serviceTemplateQueries.insertOrIgnore(
                id = template.id.value,
                studio_id = template.studioId.value,
                name = template.name,
                service_line = template.serviceLine.name,
                default_session_duration_min = template.defaultSessionDurationMinutes.toLong(),
                default_session_count = template.defaultSessionCount.toLong(),
                base_price_minor = template.basePrice?.minorUnits,
                base_price_currency = template.basePrice?.currency?.code,
                default_deliverable_count = template.defaultDeliverableCount?.toLong(),
                default_turnaround_days = template.defaultTurnaroundDays?.toLong(),
                default_revision_rounds = template.defaultRevisionRounds?.toLong(),
                notes = template.notes,
                created_at = template.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = template.audit.deletedAt.toEpochMillisOrNull(),
                version = template.audit.version.toLong(),
            )

            db.serviceTemplateQueries.update(
                name = template.name,
                serviceLine = template.serviceLine.name,
                defaultSessionDurationMin = template.defaultSessionDurationMinutes.toLong(),
                defaultSessionCount = template.defaultSessionCount.toLong(),
                basePriceMinor = template.basePrice?.minorUnits,
                basePriceCurrency = template.basePrice?.currency?.code,
                defaultDeliverableCount = template.defaultDeliverableCount?.toLong(),
                defaultTurnaroundDays = template.defaultTurnaroundDays?.toLong(),
                defaultRevisionRounds = template.defaultRevisionRounds?.toLong(),
                notes = template.notes,
                updatedAt = now,
                deletedAt = template.audit.deletedAt.toEpochMillisOrNull(),
                version = template.audit.version.toLong(),
                id = template.id.value,
            )

            db.enqueueForSync(
                template.studioId.value,
                SyncTables.SERVICE_TEMPLATE,
                template.id.value,
                OutboxOperation.Upsert,
                now,
            )
        }
    }

    override suspend fun deleteTemplate(id: ServiceTemplateId) {
        database().serviceTemplateQueries.softDelete(deletedAt = clock.now().toEpochMillis(), id = id.value)
    }

    override suspend fun seedDefaultsIfEmpty() {
        val existing = database().serviceTemplateQueries.countForStudio(studioId).awaitAsOne()
        if (existing > 0L) return

        defaultServiceTemplates(
            studioId = studioContext.studioId,
            now = clock.now(),
        ).forEach { saveTemplate(it) }
    }
}
