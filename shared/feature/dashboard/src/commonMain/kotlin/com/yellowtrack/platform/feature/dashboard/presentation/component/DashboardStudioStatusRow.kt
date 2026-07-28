package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTStatusIndicator
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.dashboard.presentation.extension.status
import com.yellowtrack.platform.feature.dashboard.presentation.extension.statusContentDescription
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatusItem

@Composable
internal fun DashboardStudioStatusRow(
    item: DashboardStudioStatusItem,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.medium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YTStatusIndicator(
            status = item.status,
            contentDescription = item.statusContentDescription,
        )
        Text(
            text = item.title,
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurface,
        )
    }
}
