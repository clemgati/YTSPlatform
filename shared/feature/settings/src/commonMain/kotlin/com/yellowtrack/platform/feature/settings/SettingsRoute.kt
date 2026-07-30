package com.yellowtrack.platform.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.feature.settings.presentation.SettingsScreen
import com.yellowtrack.platform.feature.settings.presentation.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoute(modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onRetry = {},
        onSave = viewModel::save,
        modifier = modifier,
    )
}
