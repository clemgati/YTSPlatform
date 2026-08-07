package com.yellowtrack.platform.feature.dashboard.presentation

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardAllEnquiriesSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardEnquiriesSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardHeader
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardRecentClientsSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardStudioStatusSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardTodaySessionsSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.EnquiryFormDialog
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardEnquiry
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSummary
import com.yellowtrack.platform.feature.dashboard.presentation.model.NewEnquiry

@Composable
internal fun DashboardScreen(
    uiState: DashboardUiState,
    onRetry: () -> Unit,
    onMarkEnquiryReplied: (LeadId) -> Unit,
    onSaveEnquiry: (NewEnquiry, LeadId?) -> Unit,
    onRemoveEnquiry: (LeadId) -> Unit,
    onConvertEnquiry: (LeadId, Boolean) -> Unit,
    onOpenSession: (SessionId) -> Unit,
    onOpenClient: (ClientId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEnquiryForm by remember { mutableStateOf(false) }
    var editingEnquiry by remember { mutableStateOf<DashboardEnquiry?>(null) }

    if (showEnquiryForm) {
        EnquiryFormDialog(
            onSave = {
                onSaveEnquiry(it, null)
                showEnquiryForm = false
            },
            onDismiss = { showEnquiryForm = false },
        )
    }

    editingEnquiry?.let { enquiry ->
        EnquiryFormDialog(
            onSave = {
                onSaveEnquiry(it, enquiry.id)
                editingEnquiry = null
            },
            onDismiss = { editingEnquiry = null },
            initial = enquiry.editable,
        )
    }

    StatefulContent(
        state = uiState.summary,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
        emptyContent = { emptyModifier ->
            DashboardEmptyContent(
                modifier = emptyModifier,
            )
        },
    ) { summary, contentModifier ->
        DashboardContent(
            summary = summary,
            onMarkEnquiryReplied = onMarkEnquiryReplied,
            onAddEnquiry = { showEnquiryForm = true },
            onRemoveEnquiry = onRemoveEnquiry,
            onEditEnquiry = { editingEnquiry = it },
            onConvertEnquiry = onConvertEnquiry,
            onOpenSession = onOpenSession,
            onOpenClient = onOpenClient,
            modifier = contentModifier,
        )
    }
}

@Composable
private fun DashboardContent(
    summary: DashboardSummary,
    onMarkEnquiryReplied: (LeadId) -> Unit,
    onAddEnquiry: () -> Unit,
    onRemoveEnquiry: (LeadId) -> Unit,
    onEditEnquiry: (DashboardEnquiry) -> Unit,
    onConvertEnquiry: (LeadId, Boolean) -> Unit,
    onOpenSession: (SessionId) -> Unit,
    onOpenClient: (ClientId) -> Unit,
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
        DashboardHeader(today = summary.todayLabel)

        // Placed above the schedule: an unanswered enquiry is the only thing here that
        // gets worse purely by being left alone. Shown even when empty, so there is always
        // somewhere to log one from.
        DashboardEnquiriesSection(
            enquiries = summary.enquiriesAwaitingReply,
            onMarkReplied = onMarkEnquiryReplied,
            onAddEnquiry = onAddEnquiry,
            modifier = Modifier.fillMaxWidth(),
        )

        DashboardAllEnquiriesSection(
            enquiries = summary.allEnquiries,
            onRemoveEnquiry = onRemoveEnquiry,
            onEditEnquiry = onEditEnquiry,
            onConvertEnquiry = onConvertEnquiry,
            outcomes = summary.outcomes,
            modifier = Modifier.fillMaxWidth(),
        )

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (maxWidth >= DashboardExpandedContentBreakpoint) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            YTTheme.spacing.large,
                        ),
                ) {
                    DashboardTodaySessionsSection(
                        sessions = summary.todaysSessions,
                        onOpenSession = onOpenSession,
                        modifier = Modifier.weight(1f),
                    )

                    DashboardStudioStatusSection(
                        status = summary.studioStatus,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            YTTheme.spacing.large,
                        ),
                ) {
                    DashboardTodaySessionsSection(
                        sessions = summary.todaysSessions,
                        onOpenSession = onOpenSession,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    DashboardStudioStatusSection(
                        status = summary.studioStatus,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        DashboardRecentClientsSection(
            clients = summary.recentClients,
            onOpenClient = onOpenClient,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DashboardEmptyContent(modifier: Modifier = Modifier) {
    EmptyContent(
        modifier = modifier,
        title = "Nothing scheduled yet",
        message = "Your sessions, recent clients, and studio updates will appear here.",
    )
}

private val DashboardExpandedContentBreakpoint = 720.dp
