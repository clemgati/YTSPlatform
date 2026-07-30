package com.yellowtrack.platform.feature.sessions.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * Getting the day to the people working it.
 *
 * The page above already reads as a call sheet; it has just never been able to leave the
 * laptop. Copying is offered first because it is what actually happens — a second shooter
 * is sent a message, not an attachment, and a sheet that has to be downloaded and opened
 * is one that gets read at the venue rather than the night before.
 */
@Composable
internal fun CallSheetSection(
    message: String?,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Call sheet",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            Text(
                text =
                    "Where to be, when, who else is coming, and what was promised. " +
                        "Nothing about the money goes with it.",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCopy) {
                    Text(
                        text = "Copy as text",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }

                TextButton(onClick = onSave) {
                    Text(
                        text = "Save as a web page",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }
            }

            // Says where the file went. A document someone cannot find was not saved, and
            // silence is how that happens.
            message?.let {
                Text(
                    text = it,
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
