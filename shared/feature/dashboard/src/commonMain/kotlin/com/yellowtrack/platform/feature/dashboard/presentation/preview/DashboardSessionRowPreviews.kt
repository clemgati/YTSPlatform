package com.yellowtrack.platform.feature.dashboard.presentation.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardSessionRow
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSession

@Preview
@Composable
private fun DashboardSessionRowPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.surface,
        ) {
            DashboardSessionRow(
                session =
                    DashboardSession(
                        clientName = "John Smith",
                        title = "Professional Headshots",
                        time = "10:00 AM",
                    ),
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
            )
        }
    }
}
