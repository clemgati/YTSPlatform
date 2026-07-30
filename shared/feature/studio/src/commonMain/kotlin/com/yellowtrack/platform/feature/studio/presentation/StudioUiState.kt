package com.yellowtrack.platform.feature.studio.presentation

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.studio.presentation.model.InventorySummary
import com.yellowtrack.platform.feature.studio.presentation.model.LightingRecipeItem
import kotlinx.datetime.LocalDate

internal data class StudioUiState(
    val content: UiState<StudioContent>,
)

internal data class StudioContent(
    val inventory: InventorySummary,
    val recipes: List<LightingRecipeItem>,
    val today: LocalDate,
    val currency: CurrencyCode,
)
