package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.ledger.presentation.model.ExpenseSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.RecordedCost

@Composable
internal fun ExpenseSection(
    summary: ExpenseSummary,
    onAddExpense: () -> Unit,
    /** Called for a cost the studio can correct; journeys pass nothing, having no form. */
    onCorrectCost: (RecordedCost) -> Unit = {},
    onRemoveCost: (RecordedCost) -> Unit = {},
    onAddMileage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Costs in ${summary.year}",
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            if (summary.recorded == 0) {
                Text(
                    text =
                        "No costs recorded yet. Overhead — insurance, software, rent — is what " +
                            "sets your pricing floor, so it is worth entering first.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                TextButton(onClick = onAddExpense) {
                    Text(
                        text = "Record a cost",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }

                return@Column
            }

            CostRow(
                label = "Overhead",
                value = summary.overheadTotal,
                caption = "Spread across every job",
            )
            CostRow(
                label = "Charged to jobs",
                value = summary.jobCostTotal,
                caption = "Subtracted from the revenue of a specific booking",
            )
            CostRow(
                label = "Mileage deduction",
                value = summary.mileageDeduction,
                caption = "Claimable against the rate recorded per journey",
            )

            if (summary.items.isNotEmpty()) {
                Text(
                    text = "What was spent",
                    style = YTTheme.typography.titleSmall,
                    color = YTTheme.colors.onSurface,
                )

                summary.items.forEach { cost ->
                    RecordedCostRow(
                        cost = cost,
                        onCorrect = { onCorrectCost(cost) },
                        onRemove = { onRemoveCost(cost) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium)) {
                TextButton(onClick = onAddExpense) {
                    Text(
                        text = "Record a cost",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }

                TextButton(onClick = onAddMileage) {
                    Text(
                        text = "Record a journey",
                        style = YTTheme.typography.labelLarge,
                        color = YTTheme.colors.primary,
                    )
                }
            }
        }
    }
}

/**
 * One cost, as entered.
 *
 * These were only ever totalled before, which meant a studio could record something and
 * never see it again — no way to check an amount, spot the invoice entered twice, or
 * itemise the year. The allocation is shown on every row because it is the field that
 * decides whether the money came out of one booking or off every job.
 */
@Composable
private fun RecordedCostRow(
    cost: RecordedCost,
    onCorrect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onCorrect),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cost.description,
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text = "${cost.date} · ${cost.allocation}",
                style = YTTheme.typography.labelMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        // Grouped so the figure and the control sit on one line. Left as two children of
        // the outer row they did not: the amount took the first line and "Remove" dropped
        // to the second, which reads as though it belongs to the date underneath it.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = cost.amount,
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurface,
            )

            // Removal rather than a correction to zero. The row itself is clickable to
            // correct, so this is deliberately its own target: nothing here should be
            // reachable by mis-tapping the thing beside it.
            TextButton(onClick = onRemove) {
                Text(
                    text = "Remove",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.error,
                )
            }
        }
    }
}

@Composable
private fun CostRow(
    label: String,
    value: String,
    caption: String,
) {
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
                text = label,
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text = value,
                style = YTTheme.typography.titleSmall,
                color = YTTheme.colors.onSurface,
            )
        }
        Text(
            text = caption,
            style = YTTheme.typography.bodySmall,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}
