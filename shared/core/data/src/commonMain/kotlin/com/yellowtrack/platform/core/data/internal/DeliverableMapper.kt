package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.delivery.Deliverable
import com.yellowtrack.platform.core.model.delivery.DeliverableId
import com.yellowtrack.platform.core.model.delivery.DeliverableKind
import com.yellowtrack.platform.core.model.delivery.DeliverableStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.database.Deliverable as DeliverableRow

internal fun DeliverableRow.toDomain(): Deliverable =
    Deliverable(
        id = DeliverableId(id),
        studioId = StudioId(studio_id),
        projectId = ProjectId(project_id),
        name = name,
        kind = enumOrDefault(kind, DeliverableKind.Other),
        // An unreadable status reads as not started rather than approved: work wrongly
        // marked signed off is work the client is still waiting for.
        status = enumOrDefault(status, DeliverableStatus.NotStarted),
        dueAt = due_at.toInstantOrNull(),
        deliveredAt = delivered_at.toInstantOrNull(),
        approvedAt = approved_at.toInstantOrNull(),
        revisionsUsed = revisions_used.toInt(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
