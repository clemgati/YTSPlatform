package com.yellowtrack.platform.feature.clients.presentation.project.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.delivery.DeliverableStatus
import com.yellowtrack.platform.feature.clients.presentation.project.model.DeliverableItem
import com.yellowtrack.platform.feature.clients.presentation.project.model.DeliverySummary

/**
 * What the client is owed, against what the contract promised.
 *
 * The promise is restated at the top so a studio can see what it agreed to without opening
 * the contract — and so a revision beyond the allowance reads as a decision rather than a
 * surprise.
 */
@Composable
internal fun DeliverySection(
    summary: DeliverySummary,
    onAddDeliverable: () -> Unit,
    onSetStatus: (DeliverableId, DeliverableStatus) -> Unit,
    onAddRevision: (DeliverableId) -> Unit,
    onRemove: (DeliverableId) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Delivery",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            Text(
                text = summary.promiseNote,
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )

            if (summary.deliverables.isEmpty()) {
                Text(
                    text = "Nothing promised yet.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                Text(
                    text =
                        when {
                            summary.overdue > 0 -> "${summary.overdue} past the date you promised"
                            summary.outstanding > 0 -> "${summary.outstanding} still owed"
                            else -> "Everything signed off"
                        },
                    style = YTTheme.typography.bodyMedium,
                    color =
                        when {
                            summary.overdue > 0 -> YTTheme.colors.error
                            summary.outstanding == 0 -> YTTheme.colors.primary
                            else -> YTTheme.colors.onSurfaceVariant
                        },
                )

                summary.deliverables.forEach { deliverable ->
                    HorizontalDivider(color = YTTheme.colors.outlineVariant)
                    DeliverableRow(deliverable, onSetStatus, onAddRevision, onRemove)
                }
            }

            TextButton(onClick = onAddDeliverable) {
                Text(
                    text = "Promise something",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

@Composable
private fun DeliverableRow(
    deliverable: DeliverableItem,
    onSetStatus: (DeliverableId, DeliverableStatus) -> Unit,
    onAddRevision: (DeliverableId) -> Unit,
    onRemove: (DeliverableId) -> Unit,
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
                text = deliverable.name,
                style = YTTheme.typography.bodyLarge,
                color = if (deliverable.isSettled) YTTheme.colors.onSurfaceVariant else YTTheme.colors.onSurface,
            )

            Text(
                text = deliverable.statusLabel,
                style = YTTheme.typography.titleSmall,
                color = if (deliverable.isSettled) YTTheme.colors.primary else YTTheme.colors.onSurface,
            )
        }

        deliverable.dueLabel?.let { due ->
            Text(
                text = due,
                style = YTTheme.typography.bodySmall,
                color = if (deliverable.isOverdue) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
            )
        }

        // The round about to exceed the allowance is the one still worth charging for.
        deliverable.revisionNote?.let { note ->
            Text(
                text = note,
                style = YTTheme.typography.bodySmall,
                color = if (deliverable.isBeyondAllowance) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = deliverable.kind,
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )

            Row {
                TextButton(onClick = { onRemove(deliverable.id) }) {
                    Text(
                        text = "Remove",
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                TextButton(onClick = { onAddRevision(deliverable.id) }) {
                    Text(
                        text = "Revision",
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                if (deliverable.isSettled) {
                    TextButton(onClick = { onSetStatus(deliverable.id, DeliverableStatus.InProgress) }) {
                        Text(
                            text = "Reopen",
                            style = YTTheme.typography.labelMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }
                } else {
                    TextButton(onClick = { onSetStatus(deliverable.id, DeliverableStatus.Delivered) }) {
                        Text(
                            text = "Delivered",
                            style = YTTheme.typography.labelMedium,
                            color = YTTheme.colors.primary,
                        )
                    }

                    TextButton(onClick = { onSetStatus(deliverable.id, DeliverableStatus.Approved) }) {
                        Text(
                            text = "Approved",
                            style = YTTheme.typography.labelLarge,
                            color = YTTheme.colors.primary,
                        )
                    }
                }
            }
        }
    }
}
