package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.release.ReleaseKind
import com.yellowtrack.platform.core.model.release.ReleaseStatus
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.database.Talent_release as ReleaseRow

internal fun ReleaseRow.toDomain(): TalentRelease =
    TalentRelease(
        id = TalentReleaseId(id),
        studioId = StudioId(studio_id),
        sessionId = SessionId(session_id),
        personName = person_name,
        kind = enumOrDefault(kind, ReleaseKind.Adult),
        // Defaults to Pending rather than Signed: an unreadable status must never be read
        // as permission that was never given.
        status = enumOrDefault(status, ReleaseStatus.Pending),
        signedAt = signed_at.toInstantOrNull(),
        guardianName = guardian_name,
        email = email,
        documentReference = document_reference,
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
