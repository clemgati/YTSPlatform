package com.yellowtrack.platform.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.feature.auth.presentation.SignInScreen
import com.yellowtrack.platform.feature.auth.presentation.SignInViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SignInRoute(
    version: String,
    modifier: Modifier = Modifier,
) {
    val viewModel: SignInViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SignInScreen(
        uiState = uiState,
        version = version,
        onEmailChanged = viewModel::onEmailChanged,
        onRestore = viewModel::restore,
        onDismissPendingDeletion = viewModel::dismissPendingDeletion,
        onPasswordChanged = viewModel::onPasswordChanged,
        onNameChanged = viewModel::onNameChanged,
        onStudioNameChanged = viewModel::onStudioNameChanged,
        onCodeChanged = viewModel::onCodeChanged,
        onModeChanged = viewModel::onModeChanged,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}
