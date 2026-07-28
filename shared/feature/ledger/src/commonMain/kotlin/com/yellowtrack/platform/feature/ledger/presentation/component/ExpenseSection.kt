package com.yellowtrack.platform.feature.ledger.presentation.component

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

@Composable
internal fun ExpenseSection(
    summary: ExpenseSummary,
    onAddExpense: () -> Unit,
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

            TextButton(onClick = onAddExpense) {
                Text(
                    text = "Record a cost",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
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
