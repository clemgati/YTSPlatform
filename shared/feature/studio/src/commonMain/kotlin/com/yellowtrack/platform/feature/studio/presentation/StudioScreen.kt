package com.yellowtrack.platform.feature.studio.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.studio.presentation.component.GearFormDialog
import com.yellowtrack.platform.feature.studio.presentation.component.InventorySection
import com.yellowtrack.platform.feature.studio.presentation.component.RecipeFormDialog
import com.yellowtrack.platform.feature.studio.presentation.component.RecipesSection
import com.yellowtrack.platform.feature.studio.presentation.component.RegisterSection
import com.yellowtrack.platform.feature.studio.presentation.component.VolumeFormDialog
import com.yellowtrack.platform.feature.studio.presentation.model.NewGearItem
import com.yellowtrack.platform.feature.studio.presentation.model.NewLightingRecipe
import com.yellowtrack.platform.feature.studio.presentation.model.NewVolume

@Composable
internal fun StudioScreen(
    uiState: StudioUiState,
    onRetry: () -> Unit,
    onAddGear: (NewGearItem) -> Unit,
    onMarkServiced: (GearItemId) -> Unit,
    onDeleteGear: (GearItemId) -> Unit,
    onAddRecipe: (NewLightingRecipe) -> Unit,
    onDeleteRecipe: (LightingRecipeId) -> Unit,
    onAddVolume: (NewVolume) -> Unit,
    onMarkVolumeChecked: (StorageVolumeId) -> Unit,
    onSetVolumeStatus: (StorageVolumeId, VolumeStatus) -> Unit,
    onDeleteVolume: (StorageVolumeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showGearForm by remember { mutableStateOf(false) }
    var showRecipeForm by remember { mutableStateOf(false) }
    var showVolumeForm by remember { mutableStateOf(false) }

    StatefulContent(
        state = uiState.content,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
    ) { content, contentModifier ->
        if (showGearForm) {
            GearFormDialog(
                today = content.today,
                currency = content.currency,
                onSave = {
                    onAddGear(it)
                    showGearForm = false
                },
                onDismiss = { showGearForm = false },
            )
        }

        if (showVolumeForm) {
            VolumeFormDialog(
                onSave = {
                    onAddVolume(it)
                    showVolumeForm = false
                },
                onDismiss = { showVolumeForm = false },
            )
        }

        if (showRecipeForm) {
            RecipeFormDialog(
                onSave = {
                    onAddRecipe(it)
                    showRecipeForm = false
                },
                onDismiss = { showRecipeForm = false },
            )
        }

        Column(
            modifier =
                contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(YTTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
        ) {
            InventorySection(
                inventory = content.inventory,
                onAddGear = { showGearForm = true },
                onMarkServiced = onMarkServiced,
                onDeleteGear = onDeleteGear,
            )

            RegisterSection(
                register = content.register,
                onAddVolume = { showVolumeForm = true },
                onMarkChecked = onMarkVolumeChecked,
                onSetStatus = onSetVolumeStatus,
                onDeleteVolume = onDeleteVolume,
            )

            RecipesSection(
                recipes = content.recipes,
                onAddRecipe = { showRecipeForm = true },
                onDeleteRecipe = onDeleteRecipe,
            )
        }
    }
}
