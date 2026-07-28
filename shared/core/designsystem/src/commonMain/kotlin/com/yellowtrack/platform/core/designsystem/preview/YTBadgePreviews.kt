package com.yellowtrack.platform.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme

@Preview
@Composable
private fun YTBadgePreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.background,
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
                YTBadge(
                    text = "Genesis",
                )

                YTBadge(
                    text = "10:00 AM",
                )

                YTBadge(
                    text = "Ready",
                )
            }
        }
    }
}
