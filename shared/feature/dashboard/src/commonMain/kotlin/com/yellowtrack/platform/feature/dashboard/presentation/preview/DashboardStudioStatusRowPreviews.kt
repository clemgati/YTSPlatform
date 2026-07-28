package com.yellowtrack.platform.feature.dashboard.presentation.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.dashboard.presentation.component.DashboardStudioStatusRow
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatusItem

@Preview
@Composable
private fun DashboardStudioStatusRowPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.surface,
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        YTTheme.spacing.medium,
                    ),
            ) {
                DashboardStudioStatusRow(
                    item =
                        DashboardStudioStatusItem(
                            title = "Batteries charged",
                            ready = true,
                        ),
                )

                DashboardStudioStatusRow(
                    item =
                        DashboardStudioStatusItem(
                            title = "Backdrop installed",
                            ready = false,
                        ),
                )
            }
        }
    }
}
