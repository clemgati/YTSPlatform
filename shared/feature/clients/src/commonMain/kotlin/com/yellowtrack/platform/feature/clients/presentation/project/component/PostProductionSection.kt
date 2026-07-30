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
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.feature.clients.presentation.project.model.PostProductionSummary
import com.yellowtrack.platform.feature.clients.presentation.project.model.PostTaskItem

/**
 * The work after the shoot, and how it compares to what was expected.
 *
 * The comparison is why this exists. A studio that consistently runs over its own
 * estimates is a studio whose prices are built on the wrong number of hours — and these
 * are the hours the pricing floor measures itself against once enough of them are here.
 */
@Composable
internal fun PostProductionSection(
    summary: PostProductionSummary,
    onAddTask: () -> Unit,
    onCompleteTask: (PostTaskItem) -> Unit,
    onReopenTask: (PostProductionTaskId) -> Unit,
    onDeleteTask: (PostProductionTaskId) -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Post-production",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            if (summary.tasks.isEmpty()) {
                Text(
                    text =
                        "Nothing recorded. Culling, editing, and admin are most of a job and " +
                            "the least visible part of it — until they are counted, the pricing " +
                            "floor has to guess how long they take.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                Text(
                    text = summary.headline,
                    style = YTTheme.typography.bodyMedium,
                    color =
                        if (summary.overrunHours > 0 && summary.hasEstimates) {
                            YTTheme.colors.error
                        } else {
                            YTTheme.colors.onSurfaceVariant
                        },
                )

                summary.tasks.forEach { task ->
                    HorizontalDivider(color = YTTheme.colors.outlineVariant)
                    TaskRow(task, onCompleteTask, onReopenTask, onDeleteTask)
                }
            }

            TextButton(onClick = onAddTask) {
                Text(
                    text = "Add work",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

private val PostProductionSummary.headline: String
    get() {
        val done = tasks.size - remaining

        return when {
            !hasEstimates -> "$done of ${tasks.size} done"
            remaining > 0 ->
                "$done of ${tasks.size} done • " +
                    "${actualHours.hours()} of ${estimatedHours.hours()} estimated"
            overrunHours > 0 -> "Took ${actualHours.hours()} against ${estimatedHours.hours()} estimated"
            else -> "Took ${actualHours.hours()}, inside the ${estimatedHours.hours()} estimated"
        }
    }

private fun Double.hours(): String {
    val rounded = (this * 10).toInt() / 10.0

    return if (rounded == rounded.toInt().toDouble()) "${rounded.toInt()}h" else "${rounded}h"
}

@Composable
private fun TaskRow(
    task: PostTaskItem,
    onComplete: (PostTaskItem) -> Unit,
    onReopen: (PostProductionTaskId) -> Unit,
    onDelete: (PostProductionTaskId) -> Unit,
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
                text = task.name,
                style = YTTheme.typography.bodyLarge,
                color = if (task.isDone) YTTheme.colors.onSurfaceVariant else YTTheme.colors.onSurface,
            )

            Text(
                text = task.actualLabel ?: task.estimatedLabel?.let { "$it estimated" } ?: "—",
                style = YTTheme.typography.titleSmall,
                color = YTTheme.colors.onSurface,
            )
        }

        task.overrunLabel?.let { overrun ->
            Text(
                text = overrun,
                style = YTTheme.typography.bodySmall,
                color = if (task.isOverrun) YTTheme.colors.error else YTTheme.colors.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = listOfNotNull(task.kind, task.status.name).joinToString(" • "),
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )

            Row {
                TextButton(onClick = { onDelete(task.id) }) {
                    Text(
                        text = "Remove",
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                if (task.isDone) {
                    TextButton(onClick = { onReopen(task.id) }) {
                        Text(
                            text = "Reopen",
                            style = YTTheme.typography.labelMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }
                } else {
                    TextButton(onClick = { onComplete(task) }) {
                        Text(
                            text = "Done",
                            style = YTTheme.typography.labelLarge,
                            color = YTTheme.colors.primary,
                        )
                    }
                }
            }
        }
    }
}
