package com.yellowtrack.platform.feature.sessions.presentation.model

import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus

internal data class SessionListItem(
    val id: SessionId,
    val title: String,
    val clientName: String,
    val projectName: String,
    val kind: SessionKind,
    val status: SessionStatus,
    val dayLabel: String,
    val timeRange: String,
    val locationName: String?,
    /** Shown when the session's zone differs from the device's, e.g. a destination wedding. */
    val timeZoneNote: String?,
    /**
     * The zone this session happens in.
     *
     * Editing resolves its times against this rather than the device, so correcting the
     * start of a destination wedding from home does not quietly move it by the offset
     * between the two.
     */
    val zoneId: String,
    /** The session as the form takes it, so editing opens showing what is already there. */
    val editable: NewSession,
    /** The day's good light at this place, or null where no coordinate was given. */
    val goldenHourLabel: String?,
)
