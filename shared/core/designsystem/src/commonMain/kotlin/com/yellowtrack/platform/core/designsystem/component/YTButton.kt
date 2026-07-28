package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun YTButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = YTTheme.shapes.medium,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = YTTheme.colors.primary,
                contentColor = YTTheme.colors.onPrimary,
                disabledContainerColor = YTTheme.colors.surfaceVariant,
                disabledContentColor = YTTheme.colors.onSurfaceVariant,
            ),
        contentPadding =
            PaddingValues(
                horizontal = YTTheme.spacing.large,
                vertical = 12.dp,
            ),
    ) {
        Text(
            text = text,
            style = YTTheme.typography.labelLarge,
        )
    }
}
