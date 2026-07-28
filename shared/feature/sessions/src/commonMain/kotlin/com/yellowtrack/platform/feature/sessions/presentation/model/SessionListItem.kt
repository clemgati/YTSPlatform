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
)
