package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ExpenseRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.expense.MileageId
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import com.yellowtrack.platform.core.database.Expense as ExpenseRow
import com.yellowtrack.platform.core.database.Mileage as MileageRow

internal class SqlDelightExpenseRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    ExpenseRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeExpenses(): Flow<List<Expense>> =
        observing { db ->
            db.expenseQueries
                .selectAll(studioId)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override fun observeExpensesForProject(projectId: ProjectId): Flow<List<Expense>> =
        observing { db ->
            db.expenseQueries
                .selectByProject(projectId.value)
                .asListFlow(dispatcher)
                .mapRows()
        }

    override fun observeOverheadBetween(
        fromInclusive: LocalDate,
        toExclusive: LocalDate,
    ): Flow<List<Expense>> =
        observing { db ->
            db.expenseQueries
                .selectOverheadBetween(
                    studioId = studioId,
                    // Dates are stored as ISO-8601 text, which sorts and compares
                    // lexicographically in the same order as chronologically.
                    fromInclusive = fromInclusive.toString(),
                    toExclusive = toExclusive.toString(),
                ).asListFlow(dispatcher)
                .mapRows()
        }

    override suspend fun getExpense(expenseId: ExpenseId): Expense? =
        database()
            .expenseQueries
            .selectById(expenseId.value)
            .awaitAsOneOrNull()
            ?.toDomain()

    override suspend fun saveExpense(expense: Expense) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.expenseQueries.insertOrIgnore(
                id = expense.id.value,
                studio_id = expense.studioId.value,
                category = expense.category.name,
                description = expense.description,
                amount_minor = expense.amount.minorUnits,
                amount_currency = expense.amount.currency.code,
                incurred_on = expense.incurredOn.toString(),
                project_id = expense.projectId?.value,
                vendor = expense.vendor,
                is_tax_deductible = if (expense.isTaxDeductible) 1L else 0L,
                receipt_reference = expense.receiptReference,
                notes = expense.notes,
                created_at = expense.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = expense.audit.deletedAt.toEpochMillisOrNull(),
                version = expense.audit.version.toLong(),
            )

            db.expenseQueries.update(
                category = expense.category.name,
                description = expense.description,
                amountMinor = expense.amount.minorUnits,
                amountCurrency = expense.amount.currency.code,
                incurredOn = expense.incurredOn.toString(),
                projectId = expense.projectId?.value,
                vendor = expense.vendor,
                isTaxDeductible = if (expense.isTaxDeductible) 1L else 0L,
                receiptReference = expense.receiptReference,
                notes = expense.notes,
                updatedAt = now,
                deletedAt = expense.audit.deletedAt.toEpochMillisOrNull(),
                version = expense.audit.version.toLong(),
                id = expense.id.value,
            )

            db.enqueueForSync(studioId, SyncTables.EXPENSE, expense.id.value, OutboxOperation.Upsert, now)
        }
    }

    override suspend fun deleteExpense(expenseId: ExpenseId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.expenseQueries.softDelete(deletedAt = now, id = expenseId.value)
            db.enqueueForSync(studioId, SyncTables.EXPENSE, expenseId.value, OutboxOperation.Delete, now)
        }
    }

    override fun observeMileage(): Flow<List<Mileage>> =
        observing { db ->
            db.expenseQueries
                .selectAllMileage(studioId)
                .asListFlow(dispatcher)
                .mapMileageRows()
        }

    override fun observeMileageForProject(projectId: ProjectId): Flow<List<Mileage>> =
        observing { db ->
            db.expenseQueries
                .selectMileageByProject(projectId.value)
                .asListFlow(dispatcher)
                .mapMileageRows()
        }

    override suspend fun getMileage(mileageId: MileageId): Mileage? =
        database()
            .expenseQueries
            .selectMileageByIdForSync(mileageId.value)
            .awaitAsOneOrNull()
            ?.toDomain()

    override suspend fun saveMileage(mileage: Mileage) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.expenseQueries.insertOrIgnoreMileage(
                id = mileage.id.value,
                studio_id = mileage.studioId.value,
                travelled_on = mileage.travelledOn.toString(),
                distance = mileage.distance,
                unit = mileage.unit.name,
                rate_minor = mileage.ratePerUnit.minorUnits,
                rate_currency = mileage.ratePerUnit.currency.code,
                project_id = mileage.projectId?.value,
                purpose = mileage.purpose,
                from_location = mileage.fromLocation,
                to_location = mileage.toLocation,
                created_at = mileage.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = mileage.audit.deletedAt.toEpochMillisOrNull(),
                version = mileage.audit.version.toLong(),
            )

            db.expenseQueries.updateMileage(
                travelledOn = mileage.travelledOn.toString(),
                distance = mileage.distance,
                unit = mileage.unit.name,
                rateMinor = mileage.ratePerUnit.minorUnits,
                rateCurrency = mileage.ratePerUnit.currency.code,
                projectId = mileage.projectId?.value,
                purpose = mileage.purpose,
                fromLocation = mileage.fromLocation,
                toLocation = mileage.toLocation,
                updatedAt = now,
                deletedAt = mileage.audit.deletedAt.toEpochMillisOrNull(),
                version = mileage.audit.version.toLong(),
                id = mileage.id.value,
            )

            db.enqueueForSync(studioId, SyncTables.MILEAGE, mileage.id.value, OutboxOperation.Upsert, now)
        }
    }

    override suspend fun deleteMileage(mileageId: MileageId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.expenseQueries.softDeleteMileage(deletedAt = now, id = mileageId.value)
            db.enqueueForSync(studioId, SyncTables.MILEAGE, mileageId.value, OutboxOperation.Delete, now)
        }
    }

    private fun Flow<List<ExpenseRow>>.mapRows(): Flow<List<Expense>> = map { rows -> rows.map { it.toDomain() } }

    private fun Flow<List<MileageRow>>.mapMileageRows(): Flow<List<Mileage>> =
        map { rows -> rows.map { it.toDomain() } }
}
