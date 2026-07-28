package com.yellowtrack.platform.feature.clients.presentation.list.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.clients.presentation.component.ClientsHeader
import com.yellowtrack.platform.feature.clients.presentation.list.preview.ClientsPreviewData

@Preview
@Composable
private fun ClientsHeaderPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.background,
        ) {
            ClientsHeader(
                clientCount = ClientsPreviewData.clients.size,
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
            )
        }
    }
}

@Preview
@Composable
private fun ClientsHeaderEmptyPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.background,
        ) {
            ClientsHeader(
                clientCount = 0,
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
            )
        }
    }
}
