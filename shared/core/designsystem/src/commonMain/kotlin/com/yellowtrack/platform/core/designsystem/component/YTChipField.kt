package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * Selects any number of values from a fixed list.
 *
 * Chips rather than a row of checkboxes because the lists this is used for are short
 * labels the studio scans rather than reads, and because what has been granted needs to be
 * legible at a glance — a licence is the one place where a box ticked by accident is
 * expensive.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> YTChipField(
    label: String,
    options: List<T>,
    selected: Set<T>,
    optionLabel: (T) -> String,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    help: String? = null,
    errorMessage: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        Text(
            text = label,
            style = YTTheme.typography.labelLarge,
            color = YTTheme.colors.onSurfaceVariant,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option in selected,
                    onClick = { onToggle(option) },
                    label = {
                        Text(
                            text = optionLabel(option),
                            style = YTTheme.typography.labelLarge,
                        )
                    },
                    shape = YTTheme.shapes.small,
                )
            }
        }

        val supporting = errorMessage ?: help

        if (supporting != null) {
            Text(
                text = supporting,
                style = YTTheme.typography.bodySmall,
                color = if (errorMessage != null) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}
