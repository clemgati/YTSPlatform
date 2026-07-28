package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.database.Session as SessionRow

internal fun SessionRow.toDomain(): Session =
    Session(
        id = SessionId(id),
        studioId = StudioId(studio_id),
        projectId = ProjectId(project_id),
        title = title,
        kind = enumOrDefault(kind, SessionKind.Shoot),
        status = enumOrDefault(status, SessionStatus.Scheduled),
        startsAt = starts_at.toInstant(),
        endsAt = ends_at.toInstant(),
        timeZoneId = time_zone_id,
        locationName = location_name,
        locationAddress = location_address,
        callTime = call_time.toInstantOrNull(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
