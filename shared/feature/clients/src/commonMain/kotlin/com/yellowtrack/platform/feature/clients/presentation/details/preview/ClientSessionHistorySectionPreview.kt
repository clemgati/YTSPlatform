package com.yellowtrack.platform.feature.clients.presentation.details.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.clients.presentation.details.component.ClientSessionHistorySection

@Preview
@Composable
private fun ClientSessionHistorySectionPreview() {
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
                        YTTheme.spacing.large,
                    ),
            ) {
                ClientSessionHistorySection(
                    sessions =
                        ClientDetailsPreviewData.client.sessionHistory,
                )

                ClientSessionHistorySection(
                    sessions = emptyList(),
                )
            }
        }
    }
}
