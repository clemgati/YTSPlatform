package com.yellowtrack.platform.core.designsystem.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.component.YTStatus
import com.yellowtrack.platform.core.designsystem.component.YTStatusIcon
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme

@Preview
@Composable
private fun YTStatusIconPreview() {
    YellowTrackTheme {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.medium,
                ),
        ) {
            YTStatusIcon(
                status = YTStatus.Success,
                contentDescription = null,
            )

            YTStatusIcon(
                status = YTStatus.Warning,
                contentDescription = null,
            )

            YTStatusIcon(
                status = YTStatus.Error,
                contentDescription = null,
            )

            YTStatusIcon(
                status = YTStatus.Info,
                contentDescription = null,
            )
        }
    }
}
