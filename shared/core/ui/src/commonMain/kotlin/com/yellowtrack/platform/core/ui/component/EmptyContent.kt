package com.yellowtrack.platform.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun EmptyContent(
    modifier: Modifier = Modifier,
    title: String = "Nothing here yet",
    message: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.small,
                alignment = Alignment.CenterVertically,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = YTTheme.typography.titleLarge,
            color = YTTheme.colors.onBackground,
        )

        if (message != null) {
            Text(
                text = message,
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}
