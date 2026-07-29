package com.yellowtrack.platform.feature.sessions.presentation

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
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.feature.sessions.presentation.component.SessionFormDialog
import com.yellowtrack.platform.feature.sessions.presentation.component.SessionRow
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionGroup
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionListItem
import kotlinx.datetime.TimeZone

@Composable
internal fun SessionsScreen(
    uiState: SessionsUiState,
    onRetry: () -> Unit,
    onAddSession: (NewSession) -> Unit,
    onUpdateSession: (SessionId, NewSession) -> Unit,
    onMoveSession: (SessionId, NewSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SessionListItem?>(null) }

    if (showForm && uiState.today != null) {
        SessionFormDialog(
            bookings = uiState.bookings,
            today = uiState.today,
            zone = uiState.zone,
            onSave = { session, _ ->
                onAddSession(session)
                showForm = false
            },
            onDismiss = { showForm = false },
        )
    }

    editing?.let { session ->
        SessionFormDialog(
            bookings = uiState.bookings,
            today = uiState.today ?: return@let,
            // The session's own zone, so editing a destination booking from home does not
            // shift it by the offset between the two clocks.
            zone = TimeZone.of(session.zoneId),
            initial = session.editable,
            onSave = { edited, moved ->
                if (moved) onMoveSession(session.id, edited) else onUpdateSession(session.id, edited)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    StatefulContent(
        state = uiState.groups,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
        emptyContent = { emptyModifier ->
            SessionsEmptyContent(
                onSchedule = { showForm = true },
                modifier = emptyModifier,
            )
        },
    ) { groups, contentModifier ->
        SessionsContent(
            groups = groups,
            totalCount = uiState.totalCount,
            onSessionSelected = { session -> editing = session },
            onSchedule = { showForm = true },
            modifier = contentModifier,
        )
    }
}

@Composable
private fun ScheduleSessionButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = "Schedule a session",
            style = YTTheme.typography.labelLarge,
            color = YTTheme.colors.primary,
        )
    }
}

@Composable
private fun SessionsContent(
    groups: List<SessionGroup>,
    totalCount: Int,
    onSessionSelected: (SessionListItem) -> Unit,
    onSchedule: () -> Unit,
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

        ScheduleSessionButton(onClick = onSchedule)

        groups.forEach { group ->
            YTSectionCard(
                title = group.title,
                modifier = Modifier.fillMaxWidth(),
            ) {
                group.sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        onClick = { onSessionSelected(session) },
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
private fun SessionsEmptyContent(
    onSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            message = "Open a booking for a client, then add the shoot days that belong to it.",
            action = { ScheduleSessionButton(onClick = onSchedule) },
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
