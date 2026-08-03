package com.yellowtrack.platform.feature.studio.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.common.money.formatted
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.feature.studio.presentation.model.GearGroup
import com.yellowtrack.platform.feature.studio.presentation.model.GearItemUi
import com.yellowtrack.platform.feature.studio.presentation.model.InventorySummary

/**
 * What the studio owns.
 *
 * The list itself is the least valuable part: a photographer knows what cameras they have.
 * What they do not know, until a claim is refused, is which of those cameras has no serial
 * number on file — so that is what the section leads with.
 */
@Composable
internal fun InventorySection(
    inventory: InventorySummary,
    onAddGear: () -> Unit,
    onMarkServiced: (GearItemId) -> Unit,
    onDeleteGear: (GearItemId) -> Unit,
    onEditGear: (GearItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Gear",
        modifier = modifier,
        actions = {
            TextButton(onClick = onAddGear) {
                Text(
                    text = "Add gear",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        },
    ) {
        if (inventory.itemCount == 0) {
            Text(
                text =
                    "Nothing listed yet. An inventory earns its keep the day something is stolen: " +
                        "an insurer settles on serial numbers and receipts, not on a description.",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
            return@YTSectionCard
        }

        InsuranceHeadline(inventory)

        inventory.groups.forEach { group ->
            GearGroupBlock(group, onMarkServiced, onDeleteGear, onEditGear)
        }
    }
}

@Composable
private fun InsuranceHeadline(inventory: InventorySummary) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
        Text(
            text = inventory.insuredValue.formatted(),
            style = YTTheme.typography.headlineSmall,
            color = YTTheme.colors.onSurface,
        )
        Text(
            // Said plainly, because a studio that insures for what it paid in 2019 is
            // underinsured in a way it will not discover until it claims.
            text =
                "What you paid for the ${inventory.itemCount} things you own — not what " +
                    "replacing them would cost today.",
            style = YTTheme.typography.bodySmall,
            color = YTTheme.colors.onSurfaceVariant,
        )

        inventory.warnings().forEach { warning ->
            Text(
                text = warning,
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.error,
            )
        }
    }
}

/**
 * What is wrong with the inventory, in the order it costs money.
 *
 * A missing serial number loses a claim outright; a missing price understates one; gear
 * away being repaired only changes what can be packed on Saturday.
 */
private fun InventorySummary.warnings(): List<String> =
    buildList {
        if (uninsurableNames.isNotEmpty()) {
            add(
                "${uninsurableNames.size} priced ${itemWord(
                    uninsurableNames.size,
                )} with no serial number: ${uninsurableNames.joinToString()}",
            )
        }

        if (itemsWithoutPrice > 0) {
            add("$itemsWithoutPrice ${itemWord(itemsWithoutPrice)} with no price, so the total above is short")
        }

        if (longUnservicedNames.isNotEmpty()) {
            add("Not serviced in over a year: ${longUnservicedNames.joinToString()}")
        }

        if (unavailableNames.isNotEmpty()) {
            add("Not available to pack: ${unavailableNames.joinToString()}")
        }
    }

private fun itemWord(count: Int): String = if (count == 1) "item" else "items"

@Composable
private fun GearGroupBlock(
    group: GearGroup,
    onMarkServiced: (GearItemId) -> Unit,
    onDeleteGear: (GearItemId) -> Unit,
    onEditGear: (GearItemUi) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
        Text(
            text = "${group.label} · ${group.items.size}",
            style = YTTheme.typography.titleSmall,
            color = YTTheme.colors.onSurface,
        )

        group.items.forEach { item ->
            GearRow(item, onMarkServiced, onDeleteGear, onEditGear)
        }
    }
}

/**
 * One item, with its actions beneath rather than beside it.
 *
 * They were beside it, and on a phone that left the name a word per line: three text
 * buttons take a fixed width, and the column holding the name had whatever was left, which
 * was about eighty pixels. "Sony A6700m3" arrived over three lines and the serial number —
 * the reason the row exists — was unreadable.
 *
 * Below and wrapping works at every width without a breakpoint, and costs one line of
 * height on a desktop where there was room anyway.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearRow(
    item: GearItemUi,
    onMarkServiced: (GearItemId) -> Unit,
    onDeleteGear: (GearItemId) -> Unit,
    onEditGear: (GearItemUi) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.name,
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )

            if (!item.status.isAvailable) YTBadge(text = item.statusLabel)
        }

        Text(
            text = item.detailLine(),
            style = YTTheme.typography.bodySmall,
            color = if (item.isUninsurable) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
        )

        item.notes?.let { note ->
            Text(
                text = note,
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        ) {
            TextButton(onClick = { onEditGear(item) }) {
                Text(
                    text = "Edit",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.primary,
                )
            }

            // Labelled as the action it is. "Serviced today" reads as a statement of fact,
            // and a row that appears to already assert something is not a row anyone clicks.
            TextButton(onClick = { onMarkServiced(item.id) }) {
                Text(
                    text = "Mark serviced",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.primary,
                )
            }

            TextButton(onClick = { onDeleteGear(item.id) }) {
                Text(
                    text = "Remove",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.error,
                )
            }
        }
    }
}

/** Serial, price and service history on one line — the three an insurer asks for. */
private fun GearItemUi.detailLine(): String =
    listOfNotNull(
        serialLabel ?: "No serial number".takeIf { isUninsurable },
        priceLabel,
        purchasedLabel,
        servicedLabel,
    ).joinToString(" · ")
        .ifBlank { "Nothing recorded beyond the name" }
