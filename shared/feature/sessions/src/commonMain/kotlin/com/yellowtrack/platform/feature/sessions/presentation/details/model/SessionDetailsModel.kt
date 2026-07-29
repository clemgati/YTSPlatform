package com.yellowtrack.platform.feature.sessions.presentation.details.model

import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.model.shot.ShotId
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

/** One promised photograph, as the list shows it. */
internal data class ShotItem(
    val id: ShotId,
    val description: String,
    val people: String?,
    val isCaptured: Boolean,
)

/**
 * A block of shots worked together.
 *
 * The remaining count is the figure that matters on the day: it is what tells a
 * photographer whether this group can be released or still owes a photograph.
 */
internal data class ShotGroup(
    val name: String,
    val shots: List<ShotItem>,
) {
    val remaining: Int get() = shots.count { !it.isCaptured }

    val isComplete: Boolean get() = remaining == 0
}

/** Someone working the day, as the call sheet lists them. */
internal data class CrewItem(
    val id: CrewMemberId,
    val name: String,
    val role: String,
    val phone: String?,
    /** Null when they are simply due with everyone else. */
    val callTimeLabel: String?,
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
    /** Shots promised for this day, grouped so a group can be worked and released. */
    val shotGroups: List<ShotGroup>,
    val shotsRemaining: Int,
    /** Everyone working the day, earliest call first. */
    val crew: List<CrewItem>,
    /** The session as the form takes it, so editing opens showing what is already there. */
    val editable: NewSession,
    val zoneId: String,
)
