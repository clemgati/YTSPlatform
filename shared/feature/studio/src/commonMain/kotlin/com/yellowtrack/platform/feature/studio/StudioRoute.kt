package com.yellowtrack.platform.feature.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.feature.studio.presentation.StudioScreen
import com.yellowtrack.platform.feature.studio.presentation.StudioViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StudioRoute(modifier: Modifier = Modifier) {
    val viewModel: StudioViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    StudioScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onAddGear = viewModel::addGearItem,
        onMarkServiced = viewModel::markServiced,
        onDeleteGear = viewModel::deleteGearItem,
        onAddRecipe = viewModel::addRecipe,
        onDeleteRecipe = viewModel::deleteRecipe,
        onAddVolume = viewModel::addVolume,
        onMarkVolumeChecked = viewModel::markVolumeChecked,
        onSetVolumeStatus = viewModel::setVolumeStatus,
        onDeleteVolume = viewModel::deleteVolume,
        modifier = modifier,
    )
}
