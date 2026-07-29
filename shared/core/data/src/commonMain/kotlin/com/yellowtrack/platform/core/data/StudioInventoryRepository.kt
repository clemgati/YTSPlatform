package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow

/** What the studio owns, what went out with it, and the set-ups worth repeating. */
interface GearRepository {
    fun observeGear(): Flow<List<GearItem>>

    suspend fun getGearItem(gearItemId: GearItemId): GearItem?

    suspend fun saveGearItem(item: GearItem)

    suspend fun deleteGearItem(gearItemId: GearItemId)
}

interface PackingRepository {
    fun observePackingForSession(sessionId: SessionId): Flow<List<PackingEntry>>

    suspend fun getPackingEntry(entryId: PackingEntryId): PackingEntry?

    suspend fun savePackingEntry(entry: PackingEntry)

    suspend fun deletePackingEntry(entryId: PackingEntryId)
}

interface LightingRecipeRepository {
    fun observeRecipes(): Flow<List<LightingRecipe>>

    suspend fun getRecipe(recipeId: LightingRecipeId): LightingRecipe?

    suspend fun saveRecipe(recipe: LightingRecipe)

    suspend fun deleteRecipe(recipeId: LightingRecipeId)
}
