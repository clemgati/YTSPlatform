package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.GearRepository
import com.yellowtrack.platform.core.data.LightingRecipeRepository
import com.yellowtrack.platform.core.data.PackingRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class SqlDelightGearRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    GearRepository {
    override fun observeGear(): Flow<List<GearItem>> =
        observing { db ->
            db.gearQueries
                .selectGear(studioContext.studioId.value)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override suspend fun getGearItem(gearItemId: GearItemId): GearItem? =
        observing { db ->
            db.gearQueries
                .selectGearById(gearItemId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun saveGearItem(item: GearItem) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.gearQueries.insertGearOrIgnore(
                id = item.id.value,
                studio_id = item.studioId.value,
                name = item.name,
                category = item.category.name,
                status = item.status.name,
                serial_number = item.serialNumber,
                purchase_price_minor = item.purchasePrice?.minorUnits,
                purchase_currency = item.purchasePrice?.currency?.code,
                purchased_on = item.purchasedOn?.toString(),
                last_serviced_at = item.lastServicedAt.toEpochMillisOrNull(),
                notes = item.notes,
                created_at = item.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = item.audit.deletedAt.toEpochMillisOrNull(),
                version = item.audit.version.toLong(),
            )

            db.gearQueries.updateGear(
                name = item.name,
                category = item.category.name,
                status = item.status.name,
                serialNumber = item.serialNumber,
                purchasePriceMinor = item.purchasePrice?.minorUnits,
                purchaseCurrency = item.purchasePrice?.currency?.code,
                purchasedOn = item.purchasedOn?.toString(),
                lastServicedAt = item.lastServicedAt.toEpochMillisOrNull(),
                notes = item.notes,
                updatedAt = now,
                deletedAt = item.audit.deletedAt.toEpochMillisOrNull(),
                version = item.audit.version.toLong(),
                id = item.id.value,
            )

            db.enqueueForSync(item.studioId.value, SyncTables.GEAR_ITEM, item.id.value, OutboxOperation.Upsert, now)
        }
    }

    override suspend fun deleteGearItem(gearItemId: GearItemId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Taken from the row: this repository reaches its rows through a parent, so it holds
        // no studio of its own.
        val studio =
            db.gearQueries
                .selectGearByIdForSync(gearItemId.value)
                .awaitAsOneOrNull()
                ?.studio_id ?: return

        db.transaction {
            db.gearQueries.softDeleteGear(deletedAt = now, id = gearItemId.value)

            db.enqueueForSync(studio, SyncTables.GEAR_ITEM, gearItemId.value, OutboxOperation.Delete, now)
        }
    }
}

/** Packing is reached through a session, which is already studio-scoped. */
internal class SqlDelightPackingRepository(
    provider: DatabaseProvider,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    PackingRepository {
    override fun observePackingForSession(sessionId: SessionId): Flow<List<PackingEntry>> =
        observing { db ->
            db.gearQueries
                .selectPackingForSession(sessionId.value)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override suspend fun getPackingEntry(entryId: PackingEntryId): PackingEntry? =
        observing { db ->
            db.gearQueries
                .selectPackingById(entryId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun savePackingEntry(entry: PackingEntry) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.gearQueries.insertPackingOrIgnore(
                id = entry.id.value,
                studio_id = entry.studioId.value,
                session_id = entry.sessionId.value,
                gear_item_id = entry.gearItemId.value,
                is_packed = if (entry.isPacked) 1L else 0L,
                is_returned = if (entry.isReturned) 1L else 0L,
                created_at = entry.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = entry.audit.deletedAt.toEpochMillisOrNull(),
                version = entry.audit.version.toLong(),
            )

            db.gearQueries.updatePacking(
                isPacked = if (entry.isPacked) 1L else 0L,
                isReturned = if (entry.isReturned) 1L else 0L,
                updatedAt = now,
                deletedAt = entry.audit.deletedAt.toEpochMillisOrNull(),
                version = entry.audit.version.toLong(),
                id = entry.id.value,
            )

            db.enqueueForSync(
                entry.studioId.value,
                SyncTables.PACKING_ENTRY,
                entry.id.value,
                OutboxOperation.Upsert,
                now,
            )
        }
    }

    override suspend fun deletePackingEntry(entryId: PackingEntryId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Taken from the row: this repository reaches its rows through a parent, so it holds
        // no studio of its own.
        val studio =
            db.gearQueries
                .selectPackingByIdForSync(entryId.value)
                .awaitAsOneOrNull()
                ?.studio_id ?: return

        db.transaction {
            db.gearQueries.softDeletePacking(deletedAt = now, id = entryId.value)

            db.enqueueForSync(studio, SyncTables.PACKING_ENTRY, entryId.value, OutboxOperation.Delete, now)
        }
    }
}

internal class SqlDelightLightingRecipeRepository(
    provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val dispatcher: CoroutineDispatcher,
) : DatabaseBackedRepository(provider),
    LightingRecipeRepository {
    override fun observeRecipes(): Flow<List<LightingRecipe>> =
        observing { db ->
            db.gearQueries
                .selectRecipes(studioContext.studioId.value)
                .asListFlow(dispatcher)
                .map { rows -> rows.map { it.toDomain() } }
        }

    override suspend fun getRecipe(recipeId: LightingRecipeId): LightingRecipe? =
        observing { db ->
            db.gearQueries
                .selectRecipeById(recipeId.value)
                .asOneOrNullFlow(dispatcher)
                .map { it?.toDomain() }
        }.first()

    override suspend fun saveRecipe(recipe: LightingRecipe) {
        val db = database()
        val now = clock.now().toEpochMillis()

        db.transaction {
            db.gearQueries.insertRecipeOrIgnore(
                id = recipe.id.value,
                studio_id = recipe.studioId.value,
                name = recipe.name,
                lights = encodeLights(recipe.lights),
                notes = recipe.notes,
                created_at = recipe.audit.createdAt.toEpochMillis(),
                updated_at = now,
                deleted_at = recipe.audit.deletedAt.toEpochMillisOrNull(),
                version = recipe.audit.version.toLong(),
            )

            db.gearQueries.updateRecipe(
                name = recipe.name,
                lights = encodeLights(recipe.lights),
                notes = recipe.notes,
                updatedAt = now,
                deletedAt = recipe.audit.deletedAt.toEpochMillisOrNull(),
                version = recipe.audit.version.toLong(),
                id = recipe.id.value,
            )

            db.enqueueForSync(
                recipe.studioId.value,
                SyncTables.LIGHTING_RECIPE,
                recipe.id.value,
                OutboxOperation.Upsert,
                now,
            )
        }
    }

    override suspend fun deleteRecipe(recipeId: LightingRecipeId) {
        val db = database()
        val now = clock.now().toEpochMillis()

        // Taken from the row: this repository reaches its rows through a parent, so it holds
        // no studio of its own.
        val studio =
            db.gearQueries
                .selectRecipeByIdForSync(recipeId.value)
                .awaitAsOneOrNull()
                ?.studio_id ?: return

        db.transaction {
            db.gearQueries.softDeleteRecipe(deletedAt = now, id = recipeId.value)

            db.enqueueForSync(studio, SyncTables.LIGHTING_RECIPE, recipeId.value, OutboxOperation.Delete, now)
        }
    }
}
