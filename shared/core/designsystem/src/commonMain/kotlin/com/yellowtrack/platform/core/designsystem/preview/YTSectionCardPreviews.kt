package com.yellowtrack.platform.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme

@Preview
@Composable
private fun YTSectionCardPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.background,
        ) {
            YTSectionCard(
                title = "Today's Sessions",
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
                actions = {
                    TextButton(
                        onClick = {},
                    ) {
                        Text(
                            text = "View all",
                        )
                    }
                },
            ) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            YTTheme.spacing.small,
                        ),
                ) {
                    Text(
                        text = "10:00 AM · Professional Headshots",
                        style = YTTheme.typography.bodyLarge,
                    )

                    Text(
                        text = "2:30 PM · Branding Session",
                        style = YTTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
