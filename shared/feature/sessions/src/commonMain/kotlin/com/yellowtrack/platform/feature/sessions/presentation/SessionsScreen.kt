package com.yellowtrack.platform.feature.sessions.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.sessions.presentation.component.SessionRow
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionGroup

@Composable
internal fun SessionsScreen(
    uiState: SessionsUiState,
    onRetry: () -> Unit,
    onSessionSelected: (SessionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatefulContent(
        state = uiState.groups,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
        emptyContent = { emptyModifier ->
            SessionsEmptyContent(modifier = emptyModifier)
        },
    ) { groups, contentModifier ->
        SessionsContent(
            groups = groups,
            totalCount = uiState.totalCount,
            onSessionSelected = onSessionSelected,
            modifier = contentModifier,
        )
    }
}

@Composable
private fun SessionsContent(
    groups: List<SessionGroup>,
    totalCount: Int,
    onSessionSelected: (SessionId) -> Unit,
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
        SessionsHeader(totalCount = totalCount)

        groups.forEach { group ->
            YTSectionCard(
                title = group.title,
                modifier = Modifier.fillMaxWidth(),
            ) {
                group.sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        onClick = onSessionSelected,
                    )
                }
            }
        }
    }
}

/**
 * The header stays visible when there is nothing scheduled.
 *
 * Without it the screen is an unlabelled block of centred text, and the only way to tell
 * which tab you are on is the sidebar highlight — every other screen names itself.
 */
@Composable
private fun SessionsEmptyContent(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(YTTheme.spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
    ) {
        SessionsHeader(totalCount = 0)

        EmptyContent(
            title = "No sessions scheduled",
            message = "Book a project for a client, then add the shoot days that belong to it.",
        )
    }
}

@Composable
private fun SessionsHeader(
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
    ) {
        YTBadge(text = totalCount.sessionCountLabel)

        Text(
            text = "Sessions",
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        Text(
            text = "Shoot days, scouts, and consultations across every booking.",
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}

private val Int.sessionCountLabel: String
    get() =
        when (this) {
            0 -> "No sessions"
            1 -> "1 session"
            else -> "$this sessions"
        }
