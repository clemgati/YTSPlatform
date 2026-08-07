package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ServiceTemplateRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class SqlDelightServiceTemplateRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
    private val remote: RemoteWriter,
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

        remote.write(SyncPushRequest(serviceTemplates = listOf(template)))

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
        }
    }

    /**
     * Retires a template, and tells the server before this device forgets it.
     *
     * The queued version of this had no enqueue at all until recently, and survived because
     * nothing ever called it: no screen could remove a template, so the path was never
     * walked. Going through the server removes the class of fault rather than that instance
     * — there is no second step here that can be left out, because the write *is* the send.
     */
    override suspend fun deleteTemplate(id: ServiceTemplateId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // A delete travels as the row carrying a tombstone, so it is read first.
        val existing = getTemplate(id) ?: return

        remote.write(
            SyncPushRequest(
                serviceTemplates = listOf(existing.copy(audit = existing.audit.deleted(instant(now)))),
            ),
        )

        db.transaction {
            db.serviceTemplateQueries.softDelete(deletedAt = now, id = id.value)
        }
    }

    override suspend fun seedDefaultsIfEmpty() {
        // Tombstones count. A studio that removed every template chose to, and handing the
        // four defaults back at the next launch would overrule that silently.
        val existing = database().serviceTemplateQueries.countEverForStudio(studioId).awaitAsOne()
        if (existing > 0L) return

        defaultServiceTemplates(
            studioId = studioContext.studioId,
            now = clock.now(),
        ).forEach { saveTemplate(it) }
    }

    private fun instant(millis: Long) = Instant.fromEpochMilliseconds(millis)
}
