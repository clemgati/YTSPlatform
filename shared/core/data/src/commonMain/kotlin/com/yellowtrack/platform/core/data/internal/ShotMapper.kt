package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import com.yellowtrack.platform.core.database.Shot as ShotRow

internal fun ShotRow.toDomain(): Shot =
    Shot(
        id = ShotId(id),
        studioId = StudioId(studio_id),
        sessionId = SessionId(session_id),
        description = description,
        group = group_name,
        people = people,
        position = position.toInt(),
        isCaptured = is_captured != 0L,
        capturedAt = captured_at.toInstantOrNull(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
