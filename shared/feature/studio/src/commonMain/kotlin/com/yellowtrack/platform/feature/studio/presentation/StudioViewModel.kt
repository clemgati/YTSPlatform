package com.yellowtrack.platform.feature.studio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.GearRepository
import com.yellowtrack.platform.core.data.LightingRecipeRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.data.currency
import com.yellowtrack.platform.core.data.observeCurrency
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.LightSetup
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.studio.presentation.mapper.buildInventory
import com.yellowtrack.platform.feature.studio.presentation.mapper.toItem
import com.yellowtrack.platform.feature.studio.presentation.model.NewGearItem
import com.yellowtrack.platform.feature.studio.presentation.model.NewLightingRecipe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * The studio's own things: what it owns and how it lit the last one that worked.
 *
 * Neither half is client-facing, which is exactly why they get neglected — and why an
 * uninsured body and a lighting set-up rebuilt from memory both cost real money.
 */
internal class StudioViewModel(
    private val gearRepository: GearRepository,
    private val recipeRepository: LightingRecipeRepository,
    private val studioContext: StudioContext,
    private val clock: AppClock,
    private val studioProfileRepository: StudioProfileRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StudioUiState> =
        retryTrigger
            .flatMapLatest {
                val now = clock.now()

                combine(
                    gearRepository.observeGear(),
                    recipeRepository.observeRecipes(),
                    studioProfileRepository.observeCurrency(),
                ) { gear, recipes, currency ->
                    StudioUiState(
                        content =
                            UiState.Success(
                                StudioContent(
                                    inventory = buildInventory(gear, now, timeZone, currency),
                                    recipes = recipes.map { it.toItem() },
                                    today = now.toLocalDateTime(timeZone).date,
                                    currency = currency,
                                ),
                            ),
                    )
                }.catch { throwable ->
                    emit(
                        StudioUiState(
                            content = UiState.Error(throwable.message ?: "Unable to load the studio."),
                        ),
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = StudioUiState(content = UiState.Loading),
            )

    fun retry() {
        retryTrigger.value += 1
    }

    fun addGearItem(form: NewGearItem) {
        viewModelScope.launch {
            val now = clock.now()

            gearRepository.saveGearItem(
                GearItem(
                    id = GearItemId.new(),
                    studioId = studioContext.studioId,
                    name = form.name.trim(),
                    category = form.category,
                    status = form.status,
                    serialNumber = form.serialNumber?.trim()?.ifBlank { null },
                    purchasePrice = form.purchasePrice?.let { parseMoney(it, studioProfileRepository.currency()) },
                    purchasedOn = form.purchasedOn?.toLocalDateOrNull(),
                    // Stored as an instant because a service is an event, but entered as a
                    // date because nobody remembers the hour they dropped a body off.
                    lastServicedAt = form.lastServicedOn?.toLocalDateOrNull()?.atStartOfDayIn(timeZone),
                    notes = form.notes?.trim()?.ifBlank { null },
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    /** Marks gear serviced today, which is the only service date anyone records in practice. */
    fun markServiced(gearItemId: GearItemId) {
        viewModelScope.launch {
            val existing = gearRepository.getGearItem(gearItemId) ?: return@launch

            gearRepository.saveGearItem(existing.copy(lastServicedAt = clock.now()))
        }
    }

    fun deleteGearItem(gearItemId: GearItemId) {
        viewModelScope.launch { gearRepository.deleteGearItem(gearItemId) }
    }

    fun addRecipe(form: NewLightingRecipe) {
        viewModelScope.launch {
            val now = clock.now()

            recipeRepository.saveRecipe(
                LightingRecipe(
                    id = LightingRecipeId.new(),
                    studioId = studioContext.studioId,
                    name = form.name.trim(),
                    lights =
                        form.lights.map { light ->
                            LightSetup(
                                role = light.role,
                                instrument = light.instrument.trim(),
                                modifier = light.modifier?.trim()?.ifBlank { null },
                                power = light.power?.trim()?.ifBlank { null },
                                position = light.position?.trim()?.ifBlank { null },
                                distance = light.distance?.trim()?.ifBlank { null },
                            )
                        },
                    notes = form.notes?.trim()?.ifBlank { null },
                    audit = AuditMetadata.createdAt(now),
                ),
            )
        }
    }

    fun deleteRecipe(recipeId: LightingRecipeId) {
        viewModelScope.launch { recipeRepository.deleteRecipe(recipeId) }
    }

    private fun String.toLocalDateOrNull(): LocalDate? =
        trim().takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
