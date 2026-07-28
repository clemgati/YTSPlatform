package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun YTBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier =
            modifier
                .background(
                    color = YTTheme.colors.primaryContainer,
                    shape = YTTheme.shapes.small,
                ).padding(
                    horizontal = YTTheme.spacing.small,
                    vertical = YTTheme.spacing.extraSmall,
                ),
        color = YTTheme.colors.onPrimaryContainer,
        style = YTTheme.typography.labelMedium,
    )
}
