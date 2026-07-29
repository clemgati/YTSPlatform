package com.yellowtrack.platform.feature.sessions.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.feature.sessions.presentation.details.model.PackableGear
import com.yellowtrack.platform.feature.sessions.presentation.details.model.PackingItem
import com.yellowtrack.platform.feature.sessions.presentation.details.model.PackingSummary

/**
 * What goes out with the shoot, and what comes back.
 *
 * The two ticks are separate because they are made at opposite ends of the day, and the
 * second one is the one nobody does: a light stand left behind a curtain at midnight is
 * not missed until the next booking needs it.
 */
@Composable
internal fun PackingSection(
    packing: PackingSummary,
    onAddGear: (GearItemId) -> Unit,
    onSetPacked: (PackingEntryId, Boolean) -> Unit,
    onSetReturned: (PackingEntryId, Boolean) -> Unit,
    onRemove: (PackingEntryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Kit for the day",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium)) {
            if (packing.isEmpty) {
                Text(
                    text =
                        if (packing.available.isEmpty()) {
                            "Nothing to pack yet — add gear to the studio inventory first."
                        } else {
                            "Nothing on the list. Ticking gear back in at the end of the night is " +
                                "how a stand left behind a curtain gets noticed the same evening."
                        },
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                Text(
                    text = packing.headline(),
                    style = YTTheme.typography.bodyMedium,
                    color = if (packing.missing > 0) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
                )

                packing.items.forEach { item ->
                    PackingRow(item, onSetPacked, onSetReturned, onRemove)
                }
            }

            if (packing.available.isNotEmpty()) {
                AddGearControl(packing.available, onAddGear)
            }
        }
    }
}

/**
 * What the list says right now.
 *
 * Nothing packed is a different situation from everything packed and nothing returned, and
 * only the second one is a problem — so they read differently.
 */
private fun PackingSummary.headline(): String =
    when {
        missing > 0 -> "$missing of $packed still out"
        packed == 0 -> "${items.size} listed, nothing packed yet"
        packed < items.size -> "$packed of ${items.size} packed"
        else -> "All ${items.size} packed and back"
    }

@Composable
private fun PackingRow(
    item: PackingItem,
    onSetPacked: (PackingEntryId, Boolean) -> Unit,
    onSetReturned: (PackingEntryId, Boolean) -> Unit,
    onRemove: (PackingEntryId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
        ) {
            Text(
                text = item.name,
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text = item.categoryLabel,
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        TickBox(
            label = "Packed",
            checked = item.isPacked,
            onCheckedChange = { onSetPacked(item.id, it) },
        )

        TickBox(
            label = "Back",
            checked = item.isReturned,
            isMissing = item.isPacked && !item.isReturned,
            onCheckedChange = { onSetReturned(item.id, it) },
        )

        TextButton(onClick = { onRemove(item.id) }) {
            Text(
                text = "Remove",
                style = YTTheme.typography.labelMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TickBox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isMissing: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = label,
            style = YTTheme.typography.labelMedium,
            color = if (isMissing) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddGearControl(
    available: List<PackableGear>,
    onAddGear: (GearItemId) -> Unit,
) {
    var selected by remember(available) { mutableStateOf(available.first()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        YTDropdownField(
            label = "Add gear",
            selected = selected,
            options = available,
            optionLabel = PackableGear::label,
            onSelect = { selected = it },
            modifier = Modifier.weight(1f),
        )

        TextButton(onClick = { onAddGear(selected.id) }) {
            Text(
                text = "Add",
                style = YTTheme.typography.labelLarge,
                color = YTTheme.colors.primary,
            )
        }
    }
}
