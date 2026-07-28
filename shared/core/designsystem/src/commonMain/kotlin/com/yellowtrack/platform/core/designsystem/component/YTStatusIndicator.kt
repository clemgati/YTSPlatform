package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun YTStatusIndicator(
    status: YTStatus,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    YTIcon(
        icon = status.icon,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = status.tint,
    )
}

private val YTStatus.icon
    get() =
        when (this) {
            YTStatus.Success -> YTIcons.Success
            YTStatus.Warning -> YTIcons.Warning
            YTStatus.Error -> YTIcons.Error
            YTStatus.Info -> YTIcons.Info
        }

private val YTStatus.tint: Color
    @Composable
    get() =
        when (this) {
            YTStatus.Success -> YTTheme.colors.primary
            YTStatus.Warning -> YTTheme.colors.tertiary
            YTStatus.Error -> YTTheme.colors.error
            YTStatus.Info -> YTTheme.colors.secondary
        }
