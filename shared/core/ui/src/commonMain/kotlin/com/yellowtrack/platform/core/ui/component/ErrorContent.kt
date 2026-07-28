package com.yellowtrack.platform.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun ErrorContent(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.medium,
                alignment = Alignment.CenterVertically,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Something went wrong",
            style = YTTheme.typography.titleLarge,
            color = YTTheme.colors.error,
        )

        Text(
            text = message,
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onBackground,
        )

        if (onRetry != null) {
            YTButton(
                text = "Try again",
                onClick = onRetry,
            )
        }
    }
}
