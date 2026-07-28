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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardHeader
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardRecentClientsSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardStudioStatusSection
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardTodaySessionsSection
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSummary

@Composable
internal fun DashboardScreen(
    uiState: DashboardUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            modifier = contentModifier,
        )
    }
}

@Composable
private fun DashboardContent(
    summary: DashboardSummary,
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
        DashboardHeader()

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
