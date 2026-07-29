package com.yellowtrack.platform.feature.sessions.presentation.details.model

import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.release.TalentReleaseId
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

/** One person's permission, as the session page shows it. */
internal data class ReleaseItem(
    val id: TalentReleaseId,
    val personName: String,
    val kind: String,
    val statusLabel: String,
    val isSigned: Boolean,
    /** Set when something is wrong with an otherwise-signed release. */
    val problem: String?,
)

/**
 * What has been agreed by the people photographed.
 *
 * [outstanding] is the figure that matters, because it is the number of photographs that
 * cannot lawfully be delivered yet.
 */
internal data class ReleaseSummary(
    val releases: List<ReleaseItem>,
    val outstanding: Int,
    val refused: Int,
) {
    val hasProblem: Boolean get() = outstanding > 0 || refused > 0
}

/** One recorded copy of this shoot's files. */
internal data class MediaCopyItem(
    val id: MediaCopyId,
    val volumeName: String,
    val kind: String,
    val isOffsite: Boolean,
    val isVerified: Boolean,
)

/**
 * Whether this shoot's files are actually safe.
 *
 * [shortfalls] is the part worth reading: what is missing, in the order it should be
 * fixed. The verdict alone tells a studio it is not safe; this tells it what to do next.
 */
internal data class BackupSummary(
    val copies: List<MediaCopyItem>,
    val isSatisfied: Boolean,
    val verdict: String,
    val shortfalls: List<String>,
    val unverified: Int,
)

/** One piece of gear taken to this shoot. */
internal data class PackingItem(
    val id: PackingEntryId,
    val gearItemId: GearItemId,
    val name: String,
    val categoryLabel: String,
    val isPacked: Boolean,
    val isReturned: Boolean,
)

/** Gear that could still be added to the list — everything owned and not already on it. */
internal data class PackableGear(
    val id: GearItemId,
    val label: String,
)

/**
 * What went out with the shoot, and whether it came back.
 *
 * [missing] is the figure worth surfacing. Packing is checked in a calm studio; returning
 * is checked in the dark at the end of a fourteen-hour wedding, which is exactly when a
 * light stand is left behind a curtain and not missed until the next booking.
 */
internal data class PackingSummary(
    val items: List<PackingItem>,
    val available: List<PackableGear>,
    val packed: Int,
    val missing: Int,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

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
    val releases: ReleaseSummary,
    val backup: BackupSummary,
    /** Gear taken to this shoot, and whether it came back. */
    val packing: PackingSummary,
    /** The session as the form takes it, so editing opens showing what is already there. */
    val editable: NewSession,
    val zoneId: String,
)
