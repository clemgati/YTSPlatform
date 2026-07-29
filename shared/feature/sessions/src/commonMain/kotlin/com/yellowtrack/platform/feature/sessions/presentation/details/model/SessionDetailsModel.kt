package com.yellowtrack.platform.feature.sessions.presentation.details.model

import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession

/** One line of the light panel: a window, or a moment. */
internal data class LightRow(
    val label: String,
    val value: String,
    /** True for the golden hours, which are the reason anyone opens this panel. */
    val isEmphasised: Boolean = false,
)

/**
 * The day's light at the place the shoot is.
 *
 * Null where no coordinate was given, which is most sessions. [note] carries the polar
 * cases, where a window is genuinely absent rather than merely uncomputed.
 */
internal data class SessionLight(
    val rows: List<LightRow>,
    val sunAtStart: String?,
    val note: String?,
)

internal data class SessionDetailsModel(
    val id: SessionId,
    val title: String,
    val kind: SessionKind,
    val status: SessionStatus,
    val clientName: String,
    val projectName: String,
    val dayLabel: String,
    val timeRange: String,
    val durationLabel: String,
    val callTimeLabel: String?,
    val locationName: String?,
    val locationAddress: String?,
    val coordinatesLabel: String?,
    /** Shown only when the session's zone differs from the device's. */
    val timeZoneNote: String?,
    val notes: List<String>,
    val light: SessionLight?,
    /** The session as the form takes it, so editing opens showing what is already there. */
    val editable: NewSession,
    val zoneId: String,
)
