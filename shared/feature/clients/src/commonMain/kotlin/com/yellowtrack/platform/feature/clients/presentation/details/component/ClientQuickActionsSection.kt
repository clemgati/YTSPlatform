package com.yellowtrack.platform.feature.clients.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * Archiving is deliberately absent. The button existed and did nothing — `Client` has no
 * archived state to set, so there was nothing for it to do. A control that silently
 * ignores a press is worse than one that is not offered.
 */
@Composable
internal fun ClientQuickActionsSection(
    onAddProject: () -> Unit,
    onScheduleSession: () -> Unit,
    onEditClient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Quick Actions",
        modifier = modifier,
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.small,
                ),
        ) {
            YTButton(
                text = "Open a Booking",
                onClick = onAddProject,
                modifier = Modifier.fillMaxWidth(),
            )

            YTButton(
                text = "Schedule Session",
                onClick = onScheduleSession,
                modifier = Modifier.fillMaxWidth(),
            )

            YTButton(
                text = "Edit Client",
                onClick = onEditClient,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
