package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * The lesser of two actions.
 *
 * Exists because a screen with two filled buttons has no primary action — they compete, and
 * the eye picks whichever is nearer. Anywhere a secondary choice sits beside a main one, it
 * belongs here rather than as a second [YTButton].
 */
@Composable
fun YTTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Overridden only for an action somebody should think twice about.
     *
     * The default is the accent this application uses for the thing it is encouraging, and
     * a button that ends a business rendered in the same yellow as *Sync now* reads as the
     * recommended next step. Pass `YTTheme.colors.error` for those.
     */
    contentColor: Color = YTTheme.colors.primary,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = YTTheme.shapes.medium,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = contentColor,
                disabledContentColor = YTTheme.colors.onSurfaceVariant,
            ),
        contentPadding = PaddingValues(horizontal = YTTheme.spacing.large, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = YTTheme.typography.labelLarge,
        )
    }
}
