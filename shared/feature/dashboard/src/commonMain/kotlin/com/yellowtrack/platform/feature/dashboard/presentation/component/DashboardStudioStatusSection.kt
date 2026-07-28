package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatus

@Composable
internal fun DashboardStudioStatusSection(
    status: DashboardStudioStatus,
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Studio Status",
        modifier = modifier,
    ) {
        if (status.items.isEmpty()) {
            Text(
                text = "No studio readiness items configured.",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        } else {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        YTTheme.spacing.small,
                    ),
            ) {
                status.items.forEach { item ->
                    DashboardStudioStatusRow(
                        item = item,
                    )
                }
            }
        }
    }
}
