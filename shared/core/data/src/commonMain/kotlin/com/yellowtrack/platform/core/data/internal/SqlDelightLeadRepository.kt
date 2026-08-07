package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.LeadRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.lead.Lead
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.yellowtrack.platform.core.database.Lead as LeadRow

internal class SqlDelightLeadRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
    private val remote: RemoteWriter,
) : DatabaseBackedRepository(provider),
    LeadRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeLeads(): Flow<List<Lead>> =
        observing { db ->
            db.leadQueries
                .selectAll(studioId)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override fun observeLead(leadId: LeadId): Flow<Lead?> =
        observing { db ->
            db.leadQueries
                .selectById(leadId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }

    override fun observeOpenLeads(): Flow<List<Lead>> =
        observing { db ->
            db.leadQueries
                .selectOpen(studioId)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override fun observeAwaitingResponse(): Flow<List<Lead>> =
        observing { db ->
            db.leadQueries
                .selectAwaitingResponse(studioId)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getLead(leadId: LeadId): Lead? = observeLead(leadId).first()

    override suspend fun saveLead(lead: Lead) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Both budget bounds share one currency column: a range spanning two currencies is
        // not a range anyone can act on.
        val budgetCurrency = (lead.budgetLow ?: lead.budgetHigh)?.currency?.code

        remote.write(SyncPushRequest(leads = listOf(lead)))

        db.transaction {
            db.leadQueries.insertOrIgnore(
                id = lead.id.value,
                studio_id = lead.studioId.value,
                name = lead.name,
                source = lead.source.name,
                status = lead.status.name,
                received_at = lead.receivedAt.toEpochMillis(),
                email = lead.email,
                phone = lead.phone,
                first_response_at = lead.firstResponseAt.toEpochMillisOrNull(),
                service_line = lead.serviceLine?.name,
                desired_date = lead.desiredDate?.toString(),
                budget_low_minor = lead.budgetLow?.minorUnits,
                budget_high_minor = lead.budgetHigh?.minorUnits,
                budget_currency = budgetCurrency,
                referred_by = lead.referredBy,
                lost_reason = lead.lostReason,
                converted_project_id = lead.convertedProjectId?.value,
                converted_client_id = lead.convertedClientId?.value,
                notes = lead.notes,
                created_at = lead.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = lead.audit.deletedAt.toEpochMillisOrNull(),
                version = lead.audit.version.toLong(),
            )

            db.leadQueries.update(
                name = lead.name,
                source = lead.source.name,
                status = lead.status.name,
                receivedAt = lead.receivedAt.toEpochMillis(),
                email = lead.email,
                phone = lead.phone,
                firstResponseAt = lead.firstResponseAt.toEpochMillisOrNull(),
                serviceLine = lead.serviceLine?.name,
                desiredDate = lead.desiredDate?.toString(),
                budgetLowMinor = lead.budgetLow?.minorUnits,
                budgetHighMinor = lead.budgetHigh?.minorUnits,
                budgetCurrency = budgetCurrency,
                referredBy = lead.referredBy,
                lostReason = lead.lostReason,
                convertedProjectId = lead.convertedProjectId?.value,
                convertedClientId = lead.convertedClientId?.value,
                notes = lead.notes,
                updatedAt = now,
                deletedAt = lead.audit.deletedAt.toEpochMillisOrNull(),
                version = lead.audit.version.toLong(),
                id = lead.id.value,
            )
        }
    }

    override suspend fun deleteLead(leadId: LeadId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // A delete travels as the row carrying a tombstone, so it is read first.
        val existing = getLead(leadId) ?: return

        remote.write(
            SyncPushRequest(leads = listOf(existing.copy(audit = existing.audit.deleted(instant(now))))),
        )

        db.transaction {
            db.leadQueries.softDelete(deletedAt = now, id = leadId.value)
        }
    }

    private fun Flow<List<LeadRow>>.mapRows(): Flow<List<Lead>> = map { rows -> rows.map { it.toDomain() } }

    private fun instant(millis: Long) = Instant.fromEpochMilliseconds(millis)
}
