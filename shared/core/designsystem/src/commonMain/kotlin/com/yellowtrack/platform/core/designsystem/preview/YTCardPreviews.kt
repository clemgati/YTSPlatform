package com.yellowtrack.platform.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.component.YTCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme

@Preview
@Composable
private fun YTCardPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.background,
        ) {
            YTCard(
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
            ) {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            YTTheme.spacing.small,
                        ),
                ) {
                    Text(
                        text = "Studio overview",
                        style = YTTheme.typography.titleLarge,
                    )

                    Text(
                        text = "Your cameras, lighting, and backdrop setup are ready.",
                        style = YTTheme.typography.bodyMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
