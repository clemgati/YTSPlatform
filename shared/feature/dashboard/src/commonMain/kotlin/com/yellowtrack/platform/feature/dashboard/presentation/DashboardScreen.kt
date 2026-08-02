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
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardConflictBanner
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardEnquiriesSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardHeader
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardRecentClientsSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardStudioStatusSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardTodaySessionsSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.EnquiryFormDialog
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSummary
import com.yellowtrack.platform.feature.dashboard.presentation.model.NewEnquiry

@Composable
internal fun DashboardScreen(
    uiState: DashboardUiState,
    onRetry: () -> Unit,
    onMarkEnquiryReplied: (LeadId) -> Unit,
    onAddEnquiry: (NewEnquiry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEnquiryForm by remember { mutableStateOf(false) }

    if (showEnquiryForm) {
        EnquiryFormDialog(
            onSave = {
                onAddEnquiry(it)
                showEnquiryForm = false
            },
            onDismiss = { showEnquiryForm = false },
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
            modifier = contentModifier,
        )
    }
}

@Composable
private fun DashboardContent(
    summary: DashboardSummary,
    onMarkEnquiryReplied: (LeadId) -> Unit,
    onAddEnquiry: () -> Unit,
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

        // Above everything, including the unanswered enquiries. An enquiry left alone gets
        // worse; work that synchronisation has already discarded has *already* gone wrong,
        // and the studio does not yet know.
        DashboardConflictBanner(
            count = summary.unresolvedConflicts,
            modifier = Modifier.fillMaxWidth(),
        )

        // Placed above the schedule: an unanswered enquiry is the only thing here that
        // gets worse purely by being left alone. Shown even when empty, so there is always
        // somewhere to log one from.
        DashboardEnquiriesSection(
            enquiries = summary.enquiriesAwaitingReply,
            onMarkReplied = onMarkEnquiryReplied,
            onAddEnquiry = onAddEnquiry,
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
