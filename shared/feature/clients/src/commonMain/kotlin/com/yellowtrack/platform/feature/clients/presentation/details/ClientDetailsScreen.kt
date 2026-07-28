package com.yellowtrack.platform.feature.clients.presentation.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.clients.presentation.details.component.ClientContactSection
import com.yellowtrack.platform.feature.clients.presentation.details.component.ClientDetailsHeader
import com.yellowtrack.platform.feature.clients.presentation.details.component.ClientNotesSection
import com.yellowtrack.platform.feature.clients.presentation.details.component.ClientQuickActionsSection
import com.yellowtrack.platform.feature.clients.presentation.details.component.ClientSessionHistorySection
import com.yellowtrack.platform.feature.clients.presentation.details.component.ClientUpcomingSessionSection
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientDetailsModel

@Composable
internal fun ClientDetailsScreen(
    uiState: ClientDetailsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onScheduleSession: () -> Unit,
    onEditClient: () -> Unit,
    onArchiveClient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatefulContent(
        state = uiState.client,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
        emptyContent = { emptyModifier ->
            EmptyContent(
                modifier = emptyModifier,
                title = "Client not found",
                message = "The requested client could not be loaded.",
            )
        },
    ) { client, contentModifier ->
        ClientDetailsContent(
            client = client,
            onBack = onBack,
            onScheduleSession = onScheduleSession,
            onEditClient = onEditClient,
            onArchiveClient = onArchiveClient,
            modifier = contentModifier,
        )
    }
}

@Composable
private fun ClientDetailsContent(
    client: ClientDetailsModel,
    onBack: () -> Unit,
    onScheduleSession: () -> Unit,
    onEditClient: () -> Unit,
    onArchiveClient: () -> Unit,
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
                YTTheme.spacing.extraLarge,
            ),
    ) {
        YTButton(
            text = "Back to Clients",
            onClick = onBack,
        )

        ClientDetailsHeader(
            client = client,
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (maxWidth >= ExpandedDetailsBreakpoint) {
                ExpandedClientDetailsContent(
                    client = client,
                    onScheduleSession = onScheduleSession,
                    onEditClient = onEditClient,
                    onArchiveClient = onArchiveClient,
                )
            } else {
                CompactClientDetailsContent(
                    client = client,
                    onScheduleSession = onScheduleSession,
                    onEditClient = onEditClient,
                    onArchiveClient = onArchiveClient,
                )
            }
        }
    }
}

@Composable
private fun CompactClientDetailsContent(
    client: ClientDetailsModel,
    onScheduleSession: () -> Unit,
    onEditClient: () -> Unit,
    onArchiveClient: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.extraLarge,
            ),
    ) {
        ClientUpcomingSessionSection(
            upcomingSession = client.upcomingSession,
        )

        ClientContactSection(
            contact = client.contact,
        )

        ClientSessionHistorySection(
            sessions = client.sessionHistory,
        )

        ClientNotesSection(
            notes = client.notes,
        )

        ClientQuickActionsSection(
            onScheduleSession = onScheduleSession,
            onEditClient = onEditClient,
            onArchiveClient = onArchiveClient,
        )
    }
}

@Composable
private fun ExpandedClientDetailsContent(
    client: ClientDetailsModel,
    onScheduleSession: () -> Unit,
    onEditClient: () -> Unit,
    onArchiveClient: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.extraLarge,
            ),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.extraLarge,
                ),
        ) {
            ClientUpcomingSessionSection(
                upcomingSession = client.upcomingSession,
            )

            ClientContactSection(
                contact = client.contact,
            )

            ClientQuickActionsSection(
                onScheduleSession = onScheduleSession,
                onEditClient = onEditClient,
                onArchiveClient = onArchiveClient,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.extraLarge,
                ),
        ) {
            ClientSessionHistorySection(
                sessions = client.sessionHistory,
            )

            ClientNotesSection(
                notes = client.notes,
            )
        }
    }
}

private val ExpandedDetailsBreakpoint = 840.dp
