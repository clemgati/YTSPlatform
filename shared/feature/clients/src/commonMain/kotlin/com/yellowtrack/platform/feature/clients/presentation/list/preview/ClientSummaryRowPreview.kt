package com.yellowtrack.platform.feature.clients.presentation.list.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.clients.presentation.list.component.ClientSummaryRow

@Preview
@Composable
private fun ClientSummaryRowPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.background,
        ) {
            ClientSummaryRow(
                client = ClientsPreviewData.johnSmith,
                onClick = {},
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
            )
        }
    }
}
