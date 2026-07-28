package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.ledger.presentation.PricingBasisFields
import com.yellowtrack.platform.feature.ledger.presentation.model.PackagePricing
import com.yellowtrack.platform.feature.ledger.presentation.model.PricingSummary

/**
 * What the studio must charge, and which of its packages fall short.
 *
 * The headline is the cost per sellable day rather than an annual figure, because that is
 * the number a photographer can hold in their head while quoting.
 */
@Composable
internal fun PricingSection(
    pricing: PricingSummary?,
    basis: PricingBasisFields,
    onSaveBasis: (salary: String, billableDays: String, taxRate: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Pricing floor",
        modifier = modifier.fillMaxWidth(),
    ) {
        if (pricing == null) {
            NotConfigured(basis = basis, onSaveBasis = onSaveBasis)
            return@YTSectionCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium)) {
            Text(
                text = pricing.costPerBillableDay,
                style = YTTheme.typography.headlineLarge,
                color = YTTheme.colors.onSurface,
            )

            Text(
                text = "per sellable day, across ${pricing.billableDaysPerYear} days a year",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )

            HorizontalDivider(color = YTTheme.colors.outlineVariant)

            WorkingRow("Overhead", pricing.annualOverhead)
            WorkingRow("Take-home target", pricing.targetSalary)
            WorkingRow("Tax to cover it", pricing.taxAllowance)
            WorkingRow("Total to earn", pricing.totalAnnualRequirement, emphasised = true)

            if (pricing.packages.isNotEmpty()) {
                HorizontalDivider(color = YTTheme.colors.outlineVariant)

                Text(
                    text = "Your packages",
                    style = YTTheme.typography.titleMedium,
                    color = YTTheme.colors.onSurface,
                )

                Text(
                    text =
                        "Compared against the floor. Days assume post-production takes twice " +
                            "as long as shooting — a working assumption until hours are tracked.",
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                pricing.packages.forEach { PackageRow(it) }
            }
        }
    }
}

@Composable
private fun NotConfigured(
    basis: PricingBasisFields,
    onSaveBasis: (salary: String, billableDays: String, taxRate: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium)) {
        Text(
            text =
                "Enter a take-home target, the days a year you can realistically sell, and " +
                    "your tax rate. Yellow Track will work out the least a job can be sold for " +
                    "without losing money.",
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )

        PricingSetupForm(
            initialSalary = basis.salary,
            initialBillableDays = basis.billableDays,
            initialTaxRate = basis.taxRate,
            currency = basis.currency,
            onSave = onSaveBasis,
        )
    }
}

@Composable
private fun WorkingRow(
    label: String,
    value: String,
    emphasised: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (emphasised) YTTheme.typography.titleSmall else YTTheme.typography.bodyMedium,
            color = if (emphasised) YTTheme.colors.onSurface else YTTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasised) YTTheme.typography.titleSmall else YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurface,
        )
    }
}

@Composable
private fun PackageRow(pricing: PackagePricing) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pricing.name,
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )

            if (pricing.isBelowCost) {
                YTBadge(text = "BELOW COST")
            }
        }

        Text(
            text =
                if (pricing.hasPrice) {
                    "${pricing.price} • floor ${pricing.minimumPrice} • ${pricing.estimatedDays}"
                } else {
                    "No price set • floor ${pricing.minimumPrice} • ${pricing.estimatedDays}"
                },
            style = YTTheme.typography.bodyMedium,
            color =
                if (pricing.isBelowCost) {
                    YTTheme.colors.error
                } else {
                    YTTheme.colors.onSurfaceVariant
                },
        )

        if (pricing.hasPrice) {
            Text(
                text = pricing.difference,
                style = YTTheme.typography.labelMedium,
                color = if (pricing.isBelowCost) YTTheme.colors.error else YTTheme.colors.primary,
            )
        }
    }
}
