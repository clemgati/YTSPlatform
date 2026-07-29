package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.database.Media_copy as MediaCopyRow

internal fun MediaCopyRow.toDomain(): MediaCopy =
    MediaCopy(
        id = MediaCopyId(id),
        studioId = StudioId(studio_id),
        sessionId = SessionId(session_id),
        volumeName = volume_name,
        // An unreadable kind reads as a camera card, which is excluded from the count: a
        // corrupted row must never make a studio believe it has a backup it does not.
        kind = enumOrDefault(kind, StorageKind.CameraCard),
        isOffsite = is_offsite != 0L,
        copiedAt = copied_at.toInstantOrNull(),
        verifiedAt = verified_at.toInstantOrNull(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )
