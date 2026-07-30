package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.GearRepository
import com.yellowtrack.platform.core.data.LightingRecipeRepository
import com.yellowtrack.platform.core.data.PackingRepository
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeGearRepository(
    initial: List<GearItem> = emptyList(),
) : GearRepository {
    private val state = MutableStateFlow(initial)

    override fun observeGear(): Flow<List<GearItem>> = state.map { items -> items.sortedBy { it.name } }

    override suspend fun getGearItem(gearItemId: GearItemId): GearItem? =
        state.value.firstOrNull { it.id == gearItemId }

    override suspend fun saveGearItem(item: GearItem) {
        state.value = state.value.filterNot { it.id == item.id } + item
    }

    override suspend fun deleteGearItem(gearItemId: GearItemId) {
        state.value = state.value.filterNot { it.id == gearItemId }
    }
}

class FakePackingRepository(
    initial: List<PackingEntry> = emptyList(),
) : PackingRepository {
    private val state = MutableStateFlow(initial)

    override fun observePackingForSession(sessionId: SessionId): Flow<List<PackingEntry>> =
        state.map { entries ->
            entries.filter { it.sessionId == sessionId }.sortedBy { it.audit.createdAt }
        }

    override suspend fun getPackingEntry(entryId: PackingEntryId): PackingEntry? =
        state.value.firstOrNull { it.id == entryId }

    override suspend fun savePackingEntry(entry: PackingEntry) {
        state.value = state.value.filterNot { it.id == entry.id } + entry
    }

    override suspend fun deletePackingEntry(entryId: PackingEntryId) {
        state.value = state.value.filterNot { it.id == entryId }
    }
}

class FakeLightingRecipeRepository(
    initial: List<LightingRecipe> = emptyList(),
) : LightingRecipeRepository {
    private val state = MutableStateFlow(initial)

    override fun observeRecipes(): Flow<List<LightingRecipe>> = state.map { recipes -> recipes.sortedBy { it.name } }

    override suspend fun getRecipe(recipeId: LightingRecipeId): LightingRecipe? =
        state.value.firstOrNull { it.id == recipeId }

    override suspend fun saveRecipe(recipe: LightingRecipe) {
        state.value = state.value.filterNot { it.id == recipe.id } + recipe
    }

    override suspend fun deleteRecipe(recipeId: LightingRecipeId) {
        state.value = state.value.filterNot { it.id == recipeId }
    }
}
