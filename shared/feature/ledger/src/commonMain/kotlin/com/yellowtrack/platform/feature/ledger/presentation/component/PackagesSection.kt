package com.yellowtrack.platform.feature.ledger.presentation.component

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
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.ledger.presentation.model.PackagePricing

/**
 * What the studio sells.
 *
 * Its own card rather than part of the pricing floor, because a package exists whether or
 * not a floor has been worked out. Folded into the floor, as it was, the whole list
 * disappeared until a studio entered a salary target — so a new studio could not see,
 * correct or remove the four packages it had just been given, which is the state every
 * studio starts in.
 *
 * The comparison appears on a row only when there is a floor to compare against. That is
 * the right way round: the floor is an assessment of the packages, not the other way about.
 */
@Composable
internal fun PackagesSection(
    packages: List<PackagePricing>,
    onAddPackage: () -> Unit,
    onEditPackage: (PackagePricing) -> Unit,
    onRemovePackage: (PackagePricing) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Your packages",
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium)) {
            // Shown even with nothing in it. The list used to disappear when empty, which
            // was survivable while the four seeded packages could never be removed and so
            // it never was empty. It can be now.
            if (packages.isEmpty()) {
                Text(
                    text = "Nothing here yet. A package is whatever you sell as one thing.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                Text(
                    text =
                        "Days assume post-production takes twice as long as shooting — a " +
                            "working assumption until hours are tracked.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                packages.forEach { item ->
                    PackageRow(
                        pricing = item,
                        onEdit = { onEditPackage(item) },
                        onRemove = { onRemovePackage(item) },
                    )
                }
            }

            TextButton(onClick = onAddPackage) {
                Text(
                    text = "Add a package",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PackageRow(
    pricing: PackagePricing,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
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
                text = pricing.name,
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )

            if (pricing.isBelowCost) {
                YTBadge(text = "BELOW COST")
            }
        }

        Text(
            text = pricing.summaryLine,
            style = YTTheme.typography.bodyMedium,
            color =
                if (pricing.isBelowCost) {
                    YTTheme.colors.error
                } else {
                    YTTheme.colors.onSurfaceVariant
                },
        )

        pricing.difference?.let { difference ->
            Text(
                text = difference,
                style = YTTheme.typography.labelMedium,
                color = if (pricing.isBelowCost) YTTheme.colors.error else YTTheme.colors.primary,
            )
        }

        // Named rather than hidden behind the row. The whole row used to be the edit
        // target, which nothing on screen said: a studio that did not think to press a
        // package could not change its price, and the application already argues elsewhere
        // that a control nobody can see is not a control.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        ) {
            TextButton(onClick = onEdit) {
                Text(
                    text = "Edit",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.primary,
                )
            }

            TextButton(onClick = onRemove) {
                Text(
                    text = "Remove",
                    style = YTTheme.typography.labelMedium,
                    color = YTTheme.colors.error,
                )
            }
        }
    }
}

/**
 * The row's second line, saying only what is actually known.
 *
 * Without a pricing basis there is no floor, and printing "floor —" beside a real price
 * invites a studio to read the dash as zero.
 */
private val PackagePricing.summaryLine: String
    get() =
        listOfNotNull(
            price.takeIf { hasPrice } ?: "No price set",
            minimumPrice?.let { "floor $it" },
            estimatedDays,
        ).joinToString(" • ")
