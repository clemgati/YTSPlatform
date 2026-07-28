package com.yellowtrack.platform.feature.clients.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
internal fun ClientQuickActionsSection(
    onScheduleSession: () -> Unit,
    onEditClient: () -> Unit,
    onArchiveClient: () -> Unit,
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
                text = "Schedule Session",
                onClick = onScheduleSession,
                modifier = Modifier.fillMaxWidth(),
            )

            YTButton(
                text = "Edit Client",
                onClick = onEditClient,
                modifier = Modifier.fillMaxWidth(),
            )

            YTButton(
                text = "Archive Client",
                onClick = onArchiveClient,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
