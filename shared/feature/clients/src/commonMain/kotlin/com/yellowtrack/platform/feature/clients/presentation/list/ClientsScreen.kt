package com.yellowtrack.platform.feature.clients.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.clients.presentation.component.ClientsHeader
import com.yellowtrack.platform.feature.clients.presentation.list.component.ClientSummaryRow
import com.yellowtrack.platform.feature.clients.presentation.list.model.ClientSummary

@Composable
internal fun ClientsScreen(
    uiState: ClientsUiState,
    onRetry: () -> Unit,
    onClientSelected: (ClientId) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatefulContent(
        state = uiState.clients,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
        emptyContent = { emptyModifier ->
            ClientsEmptyContent(
                modifier = emptyModifier,
            )
        },
    ) { clients, contentModifier ->
        ClientsContent(
            clients = clients,
            onClientSelected = onClientSelected,
            modifier = contentModifier,
        )
    }
}

@Composable
private fun ClientsContent(
    clients: List<ClientSummary>,
    onClientSelected: (ClientId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(YTTheme.spacing.extraLarge),
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.large,
            ),
    ) {
        ClientsHeader(
            clientCount = clients.size,
        )

        YTSectionCard(
            title = "All Clients",
            modifier = Modifier.fillMaxWidth(),
        ) {
            clients.forEach { client ->
                ClientSummaryRow(
                    client = client,
                    onClick = onClientSelected,
                )
            }
        }
    }
}

@Composable
private fun ClientsEmptyContent(modifier: Modifier = Modifier) {
    EmptyContent(
        modifier = modifier,
        title = "No clients yet",
        message = "Add your first client to begin tracking profiles and sessions.",
    )
}
