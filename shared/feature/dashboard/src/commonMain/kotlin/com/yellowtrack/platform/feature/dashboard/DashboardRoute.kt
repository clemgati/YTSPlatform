package com.yellowtrack.platform.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardScreen
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardRoute(
    onOpenSession: (SessionId) -> Unit,
    onOpenClient: (ClientId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DashboardViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onMarkEnquiryReplied = viewModel::markEnquiryReplied,
        onSaveEnquiry = viewModel::saveEnquiry,
        onRemoveEnquiry = viewModel::deleteEnquiry,
        onConvertEnquiry = viewModel::convertEnquiryToClient,
        onOpenSession = onOpenSession,
        onOpenClient = onOpenClient,
        modifier = modifier,
    )
}
