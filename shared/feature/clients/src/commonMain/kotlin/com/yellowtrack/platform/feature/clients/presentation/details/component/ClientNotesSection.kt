package com.yellowtrack.platform.feature.clients.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
internal fun ClientNotesSection(
    notes: List<String>,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Notes",
        modifier = modifier,
    ) {
        if (notes.isEmpty()) {
            Text(
                text = "No client notes.",
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurfaceVariant,
            )
        } else {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        YTTheme.spacing.small,
                    ),
            ) {
                notes.forEach { note ->
                    Text(
                        text = "• $note",
                        style = YTTheme.typography.bodyLarge,
                        color = YTTheme.colors.onSurface,
                    )
                }
            }
        }
    }
}
