package com.yellowtrack.platform.feature.clients.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTSearchField
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.clients.presentation.component.ClientFormDialog
import com.yellowtrack.platform.feature.clients.presentation.component.ClientsHeader
import com.yellowtrack.platform.feature.clients.presentation.list.component.ClientSummaryRow
import com.yellowtrack.platform.feature.clients.presentation.list.model.ClientSummary
import com.yellowtrack.platform.feature.clients.presentation.model.NewClient

@Composable
internal fun ClientsScreen(
    uiState: ClientsUiState,
    onRetry: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClientSelected: (ClientId) -> Unit,
    onAddClient: (NewClient) -> Unit,
    writeFailure: String?,
    modifier: Modifier = Modifier,
) {
    var showForm by remember { mutableStateOf(false) }

    if (showForm) {
        ClientFormDialog(
            onSave = {
                onAddClient(it)
                showForm = false
            },
            onDismiss = { showForm = false },
        )
    }

    StatefulContent(
        state = uiState.clients,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
        emptyContent = { emptyModifier ->
            ClientsEmptyContent(
                uiState = uiState,
                onQueryChange = onQueryChange,
                onAddClient = { showForm = true },
                modifier = emptyModifier,
            )
        },
    ) { clients, contentModifier ->
        ClientsContent(
            clients = clients,
            uiState = uiState,
            onQueryChange = onQueryChange,
            onClientSelected = onClientSelected,
            onAddClient = { showForm = true },
            writeFailure = writeFailure,
            modifier = contentModifier,
        )
    }
}

@Composable
private fun AddClientButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = "Add a client",
            style = YTTheme.typography.labelLarge,
            color = YTTheme.colors.primary,
        )
    }
}

@Composable
private fun ClientsContent(
    clients: List<ClientSummary>,
    uiState: ClientsUiState,
    onQueryChange: (String) -> Unit,
    onClientSelected: (ClientId) -> Unit,
    onAddClient: () -> Unit,
    writeFailure: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(YTTheme.spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
    ) {
        ClientsHeader(clientCount = clients.size)

        // A client that could not be saved. Clients require a connection under ADR 0012, so
        // this is the difference between "we could not reach the server" and a row silently
        // not appearing — which is what it looked like before this was here.
        writeFailure?.let { message ->
            Text(
                text = message,
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        YTSearchField(
            value = uiState.query,
            onValueChange = onQueryChange,
            placeholder = "Search clients",
            contentDescription = "Search clients by name, company, or contact",
        )

        YTSectionCard(
            title = if (uiState.isSearching) "Results" else "All Clients",
            modifier = Modifier.fillMaxWidth(),
        ) {
            clients.forEach { client ->
                ClientSummaryRow(
                    client = client,
                    onClick = onClientSelected,
                )
            }

            AddClientButton(onClick = onAddClient)
        }
    }
}

/**
 * The search field stays visible when there are no results, because a search matching
 * nothing must not remove the only control that could correct it.
 */
@Composable
private fun ClientsEmptyContent(
    uiState: ClientsUiState,
    onQueryChange: (String) -> Unit,
    onAddClient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(YTTheme.spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
    ) {
        ClientsHeader(clientCount = 0)

        YTSearchField(
            value = uiState.query,
            onValueChange = onQueryChange,
            placeholder = "Search clients",
            contentDescription = "Search clients by name, company, or contact",
        )

        if (uiState.isSearching) {
            EmptyContent(
                title = "No matches",
                message = "No clients match \"${uiState.query}\". Try a different name or company.",
            )
        } else {
            // The empty state has invited this since 0.3.0 with no way to accept.
            EmptyContent(
                title = "No clients yet",
                message = "Add your first client to begin tracking profiles and sessions.",
                action = { AddClientButton(onClick = onAddClient) },
            )
        }
    }
}
