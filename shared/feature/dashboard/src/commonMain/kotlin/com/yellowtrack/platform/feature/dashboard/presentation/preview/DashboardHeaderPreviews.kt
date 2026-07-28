package com.yellowtrack.platform.feature.dashboard.presentation.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardHeader

@Preview
@Composable
private fun DashboardHeaderPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.background,
        ) {
            DashboardHeader(
                today = "Saturday, June 13",
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
            )
        }
    }
}
