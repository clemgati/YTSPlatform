package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ContractRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.contract.Contract
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.contract.ContractStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class SqlDelightContractRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    ContractRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeContracts(): Flow<List<Contract>> =
        observing { db ->
            db.contractQueries
                .selectAll(studioId)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override fun observeContract(contractId: ContractId): Flow<Contract?> =
        observing { db ->
            db.contractQueries
                .selectById(contractId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }

    override fun observeContractsForProject(projectId: ProjectId): Flow<List<Contract>> =
        observing { db ->
            db.contractQueries
                .selectByProject(projectId.value)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override fun observeAwaitingSignature(): Flow<List<Contract>> =
        observeContracts().map { contracts ->
            contracts
                .filter { it.status == ContractStatus.Sent }
                .sortedBy { it.sentAt ?: it.audit.createdAt }
        }

    override suspend fun getContract(contractId: ContractId): Contract? = observeContract(contractId).first()

    override suspend fun saveContract(contract: Contract) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.contractQueries.insertOrIgnore(
                id = contract.id.value,
                studio_id = contract.studioId.value,
                project_id = contract.projectId.value,
                title = contract.title,
                status = contract.status.name,
                sent_at = contract.sentAt.toEpochMillisOrNull(),
                signed_at = contract.signedAt.toEpochMillisOrNull(),
                signer_name = contract.signerName,
                signer_email = contract.signerEmail,
                retainer_minor = contract.retainerAmount?.minorUnits,
                retainer_currency = contract.retainerAmount?.currency?.code,
                is_retainer_refundable = if (contract.isRetainerRefundable) 1L else 0L,
                turnaround_days = contract.turnaroundDays?.toLong(),
                revision_rounds = contract.revisionRounds?.toLong(),
                cancellation_terms = contract.cancellationTerms,
                reschedule_terms = contract.rescheduleTerms,
                weather_clause = contract.weatherClause,
                usage_license = encodeUsageLicense(contract.usageLicense),
                document_reference = contract.documentReference,
                notes = contract.notes,
                created_at = contract.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = contract.audit.deletedAt.toEpochMillisOrNull(),
                version = contract.audit.version.toLong(),
            )

            db.contractQueries.update(
                projectId = contract.projectId.value,
                title = contract.title,
                status = contract.status.name,
                sentAt = contract.sentAt.toEpochMillisOrNull(),
                signedAt = contract.signedAt.toEpochMillisOrNull(),
                signerName = contract.signerName,
                signerEmail = contract.signerEmail,
                retainerMinor = contract.retainerAmount?.minorUnits,
                retainerCurrency = contract.retainerAmount?.currency?.code,
                isRetainerRefundable = if (contract.isRetainerRefundable) 1L else 0L,
                turnaroundDays = contract.turnaroundDays?.toLong(),
                revisionRounds = contract.revisionRounds?.toLong(),
                cancellationTerms = contract.cancellationTerms,
                rescheduleTerms = contract.rescheduleTerms,
                weatherClause = contract.weatherClause,
                usageLicense = encodeUsageLicense(contract.usageLicense),
                documentReference = contract.documentReference,
                notes = contract.notes,
                updatedAt = now,
                deletedAt = contract.audit.deletedAt.toEpochMillisOrNull(),
                version = contract.audit.version.toLong(),
                id = contract.id.value,
            )
        }
    }

    override suspend fun deleteContract(contractId: ContractId) {
        database().contractQueries.softDelete(deletedAt = clock.now().toEpochMillis(), id = contractId.value)
    }
}
