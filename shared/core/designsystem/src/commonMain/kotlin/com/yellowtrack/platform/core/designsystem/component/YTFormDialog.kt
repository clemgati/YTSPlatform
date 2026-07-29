package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * A dialog holding a short create-or-edit form.
 *
 * Features present their own forms this way rather than pushing an application route, so
 * that a form stays inside the feature that owns it — a feature's public surface remains
 * its Route, and the app module does not learn about "add expense".
 *
 * A dialog also behaves acceptably on all four targets. On a phone a full screen would be
 * better; that is worth revisiting if these forms grow past a handful of fields.
 */
@Composable
fun YTFormDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
    dismissLabel: String = "Cancel",
    supportingText: String? = null,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = YTTheme.shapes.large,
        containerColor = YTTheme.colors.surface,
        title = {
            Text(
                text = title,
                style = YTTheme.typography.headlineSmall,
                color = YTTheme.colors.onSurface,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // Bounded so a long form scrolls rather than pushing the buttons
                        // off a short window.
                        .heightIn(max = MaxFormHeight)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium),
            ) {
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = YTTheme.typography.bodyMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = YTTheme.spacing.extraSmall),
                    )
                }

                content()
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
            ) {
                Text(
                    text = confirmLabel,
                    style = YTTheme.typography.labelLarge,
                    color =
                        if (confirmEnabled) {
                            YTTheme.colors.primary
                        } else {
                            YTTheme.colors.onSurfaceVariant
                        },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissLabel,
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * How tall a form may grow before it scrolls.
 *
 * Raised from 420dp once the contract form arrived: at 420dp only five of its fourteen
 * fields were reachable, in a window with room for far more. The cap turned out not to be
 * what protects the buttons — Material's own dialog clamps its content to the space
 * available, verified by rendering this dialog into a 280dp-tall scene, shorter than any
 * real phone, where Cancel and the confirm button both stayed on screen.
 */
private val MaxFormHeight = 560.dp
