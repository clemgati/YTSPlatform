package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.database.Crew_member as CrewRow

internal fun CrewRow.toDomain(): CrewMember =
    CrewMember(
        id = CrewMemberId(id),
        studioId = StudioId(studio_id),
        sessionId = SessionId(session_id),
        name = name,
        role = enumOrDefault(role, CrewRole.Other),
        phone = phone,
        callTime = call_time.toInstantOrNull(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
