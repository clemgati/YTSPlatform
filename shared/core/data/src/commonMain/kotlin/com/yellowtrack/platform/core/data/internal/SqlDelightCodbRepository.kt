package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.money.sum
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.CodbRepository
import com.yellowtrack.platform.core.data.ExpenseRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.codb.CodbBreakdown
import com.yellowtrack.platform.core.model.codb.CodbProfile
import com.yellowtrack.platform.core.model.codb.CostOfDoingBusiness
import com.yellowtrack.platform.core.model.expense.Expense
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

internal class SqlDelightCodbRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
    private val expenseRepository: ExpenseRepository,
) : DatabaseBackedRepository(provider),
    CodbRepository {
    private val studioId get() = studioContext.studioId.value

    override fun observeProfile(): Flow<CodbProfile?> =
        observing { db ->
            db.codbQueries
                .selectForStudio(studioId)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }

    override fun observeBreakdown(year: Int): Flow<CodbBreakdown?> =
        combine(
            observeProfile(),
            expenseRepository.observeOverheadBetween(
                fromInclusive = LocalDate(year, 1, 1),
                toExclusive = LocalDate(year + 1, 1, 1),
            ),
        ) { profile, overheadExpenses ->
            profile?.let {
                CostOfDoingBusiness.calculate(
                    profile = it,
                    overheadFromExpenses = overheadExpenses.totalIn(it),
                )
            }
        }

    override suspend fun getProfile(): CodbProfile? = observeProfile().first()

    override suspend fun saveProfile(profile: CodbProfile) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.codbQueries.insertOrIgnore(
                id = profile.id.value,
                studio_id = profile.studioId.value,
                currency = profile.currency.code,
                target_annual_salary_minor = profile.targetAnnualSalary.minorUnits,
                billable_days_per_year = profile.billableDaysPerYear.toLong(),
                tax_rate_basis_points = profile.taxRateBasisPoints.toLong(),
                annual_overhead_minor = profile.annualOverheadOverride?.minorUnits,
                profit_margin_basis_points = profile.desiredProfitMarginBasisPoints.toLong(),
                created_at = profile.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = profile.audit.deletedAt.toEpochMillisOrNull(),
                version = profile.audit.version.toLong(),
            )

            db.codbQueries.update(
                currency = profile.currency.code,
                targetAnnualSalaryMinor = profile.targetAnnualSalary.minorUnits,
                billableDaysPerYear = profile.billableDaysPerYear.toLong(),
                taxRateBasisPoints = profile.taxRateBasisPoints.toLong(),
                annualOverheadMinor = profile.annualOverheadOverride?.minorUnits,
                profitMarginBasisPoints = profile.desiredProfitMarginBasisPoints.toLong(),
                updatedAt = now,
                deletedAt = profile.audit.deletedAt.toEpochMillisOrNull(),
                version = profile.audit.version.toLong(),
                id = profile.id.value,
            )

            db.enqueueForSync(
                profile.studioId.value,
                SyncTables.CODB_PROFILE,
                profile.id.value,
                OutboxOperation.Upsert,
                now,
            )
        }
    }

    /**
     * Sums overhead, ignoring anything recorded in another currency.
     *
     * Silently converting would require an exchange rate the platform does not have, and
     * inventing one would corrupt the very number a studio prices against.
     */
    private fun List<Expense>.totalIn(profile: CodbProfile): Money =
        filter { it.amount.currency == profile.currency }
            .map(Expense::amount)
            .sum(profile.currency)
}
