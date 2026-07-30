package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.post.PostTaskKind
import com.yellowtrack.platform.core.model.post.PostTaskStatus
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.database.Post_task as PostTaskRow

internal fun PostTaskRow.toDomain(): PostProductionTask =
    PostProductionTask(
        id = PostProductionTaskId(id),
        studioId = StudioId(studio_id),
        projectId = ProjectId(project_id),
        name = name,
        kind = enumOrDefault(kind, PostTaskKind.Other),
        // An unreadable status reads as not started rather than done: work wrongly marked
        // finished is work nobody goes back to.
        status = enumOrDefault(status, PostTaskStatus.ToDo),
        estimatedHours = estimated_hours,
        actualHours = actual_hours,
        completedAt = completed_at.toInstantOrNull(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
