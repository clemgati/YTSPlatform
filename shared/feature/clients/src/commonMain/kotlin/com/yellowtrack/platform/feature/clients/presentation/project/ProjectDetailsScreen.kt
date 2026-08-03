package com.yellowtrack.platform.feature.clients.presentation.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.delivery.DeliverableStatus
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.ui.component.EmptyContent
import com.yellowtrack.platform.core.ui.component.StatefulContent
import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.feature.clients.presentation.details.component.ProjectFormDialog
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject
import com.yellowtrack.platform.feature.clients.presentation.project.component.CompleteTaskDialog
import com.yellowtrack.platform.feature.clients.presentation.project.component.DeliverableFormDialog
import com.yellowtrack.platform.feature.clients.presentation.project.component.DeliverySection
import com.yellowtrack.platform.feature.clients.presentation.project.component.PostProductionSection
import com.yellowtrack.platform.feature.clients.presentation.project.component.PostTaskFormDialog
import com.yellowtrack.platform.feature.clients.presentation.project.model.NewDeliverable
import com.yellowtrack.platform.feature.clients.presentation.project.model.NewPostTask
import com.yellowtrack.platform.feature.clients.presentation.project.model.PaperworkItem
import com.yellowtrack.platform.feature.clients.presentation.project.model.PostTaskItem
import com.yellowtrack.platform.feature.clients.presentation.project.model.ProjectDetailsModel

@Composable
internal fun ProjectDetailsScreen(
    uiState: ProjectDetailsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSessionSelected: (SessionId) -> Unit,
    onAddTask: (NewPostTask) -> Unit,
    onCompleteTask: (PostProductionTaskId, String) -> Unit,
    onReopenTask: (PostProductionTaskId) -> Unit,
    onDeleteTask: (PostProductionTaskId) -> Unit,
    onUpdateProject: (NewProject) -> Unit,
    onAddDeliverable: (NewDeliverable) -> Unit,
    onSetDeliverableStatus: (DeliverableId, DeliverableStatus) -> Unit,
    onAddRevision: (DeliverableId) -> Unit,
    onRemoveDeliverable: (DeliverableId) -> Unit,
    onRemoveProject: () -> Unit,
    onRemovePaperwork: (PaperworkItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var addingTask by remember { mutableStateOf(false) }
    var completing by remember { mutableStateOf<PostTaskItem?>(null) }
    var editing by remember { mutableStateOf(false) }
    var promising by remember { mutableStateOf(false) }
    var confirmRemoval by remember { mutableStateOf(false) }

    StatefulContent(
        state = uiState.project,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
        emptyContent = { emptyModifier ->
            EmptyContent(
                modifier = emptyModifier,
                title = "Booking not found",
                message = "This booking could not be loaded.",
            )
        },
    ) { project, contentModifier ->
        if (addingTask) {
            PostTaskFormDialog(
                onSave = {
                    onAddTask(it)
                    addingTask = false
                },
                onDismiss = { addingTask = false },
            )
        }

        completing?.let { task ->
            CompleteTaskDialog(
                task = task,
                onComplete = { hours ->
                    onCompleteTask(task.id, hours)
                    completing = null
                },
                onDismiss = { completing = null },
            )
        }

        if (promising) {
            DeliverableFormDialog(
                promiseNote = project.delivery.promiseNote,
                onSave = {
                    onAddDeliverable(it)
                    promising = false
                },
                onDismiss = { promising = false },
            )
        }

        if (editing) {
            ProjectFormDialog(
                clientName = project.clientName,
                currency = uiState.currency,
                initial = project.editable,
                onSave = {
                    onUpdateProject(it)
                    editing = false
                },
                onDismiss = { editing = false },
            )
        }

        if (confirmRemoval) {
            YTFormDialog(
                title = "Remove ${project.name}?",
                confirmLabel = "Remove",
                supportingText =
                    "The booking goes from every device. Nothing is attached to it, so " +
                        "nothing else is lost. This cannot be undone.",
                onConfirm = {
                    onRemoveProject()
                    confirmRemoval = false
                },
                onDismiss = { confirmRemoval = false },
                content = {},
            )
        }

        Column(
            modifier =
                contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(YTTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraLarge),
        ) {
            YTButton(text = "Back", onClick = onBack)

            ProjectHeader(project)

            YTDetailSection(title = "The booking") {
                Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                    project.valueLabel?.let { DetailLine("Agreed value", it) }
                    project.enquiredLabel?.let { DetailLine("Enquiry", it.removePrefix("Enquired ")) }
                    project.bookedLabel?.let { DetailLine("Booked", it.removePrefix("Booked ")) }

                    if (project.valueLabel == null) {
                        Text(
                            text = "No figure agreed yet.",
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }

            YTDetailSection(title = "Shoot days") {
                Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
                    if (project.sessions.isEmpty()) {
                        Text(
                            text = "Nothing scheduled on this booking yet.",
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }

                    project.sessions.forEachIndexed { index, session ->
                        if (index > 0) HorizontalDivider(color = YTTheme.colors.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    style = YTTheme.typography.bodyLarge,
                                    color = YTTheme.colors.onSurface,
                                )
                                Text(
                                    text = "${session.dayLabel} • ${session.timeRange} • ${session.statusLabel}",
                                    style = YTTheme.typography.bodyMedium,
                                    color = YTTheme.colors.onSurfaceVariant,
                                )
                            }

                            TextButton(onClick = { onSessionSelected(session.id) }) {
                                Text(
                                    text = "Open",
                                    style = YTTheme.typography.labelLarge,
                                    color = YTTheme.colors.primary,
                                )
                            }
                        }
                    }
                }
            }

            PostProductionSection(
                summary = project.postProduction,
                onAddTask = { addingTask = true },
                onCompleteTask = { completing = it },
                onReopenTask = onReopenTask,
                onDeleteTask = onDeleteTask,
            )

            DeliverySection(
                summary = project.delivery,
                onAddDeliverable = { promising = true },
                onSetStatus = onSetDeliverableStatus,
                onAddRevision = onAddRevision,
                onRemove = onRemoveDeliverable,
            )

            if (project.notes.isNotEmpty()) {
                YTDetailSection(title = "Notes") {
                    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                        project.notes.forEach { note ->
                            Text(
                                text = note,
                                style = YTTheme.typography.bodyMedium,
                                color = YTTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            PaperworkSection(
                paperwork = project.paperwork,
                onRemove = onRemovePaperwork,
            )

            TextButton(onClick = { editing = true }) {
                Text(
                    text = "Edit this booking",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }

            RemoveBookingAction(
                removal = project.removal,
                onRemove = { confirmRemoval = true },
            )
        }
    }
}

/**
 * Every quote and contract on this booking, whatever became of them.
 *
 * The Ledger shows a quote only while it awaits a decision and a contract only until it
 * holds its date, which means the successful ones — accepted, signed, paid — leave the
 * application entirely. They still exist, they still hold the booking in place, and until
 * now nothing anywhere would show them.
 *
 * The state is spelled out on every row rather than only the ones needing attention,
 * because "Accepted" next to "Draft" is how a studio spots the duplicate quote it raised
 * twice and accepted once.
 */
@Composable
private fun PaperworkSection(
    paperwork: List<PaperworkItem>,
    onRemove: (PaperworkItem) -> Unit,
) {
    if (paperwork.isEmpty()) return

    YTDetailSection(title = "Quotes and contracts") {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            paperwork.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.onSurface,
                        )
                        Text(
                            text = listOfNotNull(item.statusLabel, item.amount).joinToString(" · "),
                            style = YTTheme.typography.labelMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }

                    TextButton(onClick = { onRemove(item) }) {
                        Text(
                            text = "Remove",
                            style = YTTheme.typography.labelLarge,
                            color = YTTheme.colors.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Removal, and the reason it is refused when it is.
 *
 * The reason is the useful half. A booking is held by anything at all attached to it, and
 * a studio told only "no" has to guess whether it is the invoice, the shoot day or the
 * deliverable in the way — so it is named and counted. Some of those things cannot be
 * removed from any screen yet either, which is worth saying plainly rather than hiding: it
 * makes the chain visible instead of leaving the control looking broken.
 */
@Composable
private fun RemoveBookingAction(
    removal: Removal,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
        TextButton(
            onClick = onRemove,
            enabled = removal is Removal.Available,
        ) {
            Text(
                text = "Remove this booking",
                style = YTTheme.typography.labelLarge,
                color =
                    when (removal) {
                        is Removal.Available -> YTTheme.colors.error
                        else -> YTTheme.colors.onSurfaceVariant
                    },
            )
        }

        if (removal is Removal.HeldBy) {
            Text(
                text = "This booking has ${removal.summary} on it. Remove them first.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProjectHeader(project: ProjectDetailsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
        YTBadge(text = project.status.name)

        Text(
            text = project.name,
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        Text(
            text =
                listOfNotNull(project.serviceLine, project.clientName.ifBlank { null })
                    .joinToString(" • "),
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )
        Text(
            text = value,
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurface,
        )
    }
}
