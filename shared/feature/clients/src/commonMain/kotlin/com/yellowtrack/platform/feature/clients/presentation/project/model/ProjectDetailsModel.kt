package com.yellowtrack.platform.feature.clients.presentation.project.model

import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.post.PostTaskStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.feature.clients.presentation.details.model.NewProject

/** A shoot day on this booking. */
internal data class BookingSessionItem(
    val id: SessionId,
    val title: String,
    val dayLabel: String,
    val timeRange: String,
    val statusLabel: String,
)

/** One piece of post-production, and how it compares to what was expected. */
internal data class PostTaskItem(
    val id: PostProductionTaskId,
    val name: String,
    val kind: String,
    val status: PostTaskStatus,
    val estimatedLabel: String?,
    val actualLabel: String?,
    /**
     * How far over the estimate it ran, once finished.
     *
     * Null while the work is open: a task half done has not overrun, and saying it has
     * would make every job in progress look late.
     */
    val overrunLabel: String?,
    val isOverrun: Boolean,
) {
    val isDone: Boolean get() = status == PostTaskStatus.Done
}

/**
 * What post-production has cost this booking so far.
 *
 * The comparison is the point: a studio that consistently runs over its own estimates is
 * a studio whose prices are built on the wrong number of hours.
 */
internal data class PostProductionSummary(
    val tasks: List<PostTaskItem>,
    val estimatedHours: Double,
    val actualHours: Double,
    val remaining: Int,
) {
    val hasEstimates: Boolean get() = estimatedHours > 0.0

    /** Only meaningful once something has been finished and timed. */
    val overrunHours: Double get() = actualHours - estimatedHours
}

/** One thing promised to the client, and how it stands against what was agreed. */
internal data class DeliverableItem(
    val id: DeliverableId,
    val name: String,
    val kind: String,
    val statusLabel: String,
    val dueLabel: String?,
    val isOverdue: Boolean,
    val revisionsUsed: Int,
    /** How the rounds used compare to the contract's allowance, where there is one. */
    val revisionNote: String?,
    val isBeyondAllowance: Boolean,
    val isSettled: Boolean,
)

/**
 * What the client is still owed.
 *
 * [promiseNote] restates the contract's turnaround and revision allowance, so a studio can
 * see what it agreed to without opening the contract.
 */
internal data class DeliverySummary(
    val deliverables: List<DeliverableItem>,
    val outstanding: Int,
    val overdue: Int,
    val promiseNote: String,
)

internal data class ProjectDetailsModel(
    val id: ProjectId,
    val name: String,
    val clientName: String,
    val serviceLine: String,
    val status: ProjectStatus,
    val valueLabel: String?,
    val enquiredLabel: String?,
    val bookedLabel: String?,
    val notes: List<String>,
    val sessions: List<BookingSessionItem>,
    val postProduction: PostProductionSummary,
    val delivery: DeliverySummary,
    /** Whether this booking can be removed, and what is holding it if not. */
    val removal: Removal,
    /** The booking as the form takes it, so editing opens showing what is already there. */
    val editable: NewProject,
)
