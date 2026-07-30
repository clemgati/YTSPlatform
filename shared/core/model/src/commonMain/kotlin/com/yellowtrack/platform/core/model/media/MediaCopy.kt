package com.yellowtrack.platform.core.model.media

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class MediaCopyId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): MediaCopyId = MediaCopyId(uuidV7().toString())
    }
}

/**
 * What kind of thing the files are sitting on.
 *
 * The distinction that matters is not the brand but the failure mode: two external drives
 * bought together fail the same way, and the 3-2-1 rule asks for two different kinds
 * precisely because one kind failing should not take everything.
 */
@Serializable
enum class StorageKind {
    /** The card that came out of the camera. Not a backup until it is copied off. */
    CameraCard,

    /** The machine the edit happens on. */
    Computer,

    ExternalDrive,

    /** Network storage in the studio. Still on the premises. */
    Nas,

    /** Object storage or a backup service. Offsite by definition. */
    Cloud,

    /** A drive kept somewhere else — a relative's house, a safe deposit box. */
    OffsiteDrive,
    ;

    /** Whether a copy on this is necessarily away from the studio. */
    val isInherentlyOffsite: Boolean get() = this == Cloud || this == OffsiteDrive
}

/**
 * One copy of a shoot's files, somewhere.
 *
 * A card still in the camera bag is not a backup, and two copies on the same desk are one
 * flood away from none. [StorageKind] and [isOffsite] exist so the 3-2-1 rule can be
 * checked rather than merely believed — see `BackupHealth`.
 *
 * @param verifiedAt when the copy was last checked to be readable. A backup nobody has
 *   opened is a backup nobody knows they have; a drive can fail silently and the studio
 *   finds out on the day it is needed.
 */
@Serializable
data class MediaCopy(
    val id: MediaCopyId,
    override val studioId: StudioId,
    val sessionId: SessionId,
    /**
     * The drive in the studio's register this copy sits on.
     *
     * Null for copies recorded before there was a register, and for a studio that has not
     * built one. [volumeName] remains the label of last resort so those copies still read
     * as something rather than as a blank row.
     */
    val volumeId: StorageVolumeId? = null,
    /** What the studio calls it — "Red Samsung T7", "Studio NAS", "Backblaze". */
    val volumeName: String,
    val kind: StorageKind,
    val isOffsite: Boolean = false,
    val copiedAt: Instant? = null,
    val verifiedAt: Instant? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    /** Cloud and offsite drives are away by definition; anything else is a studio decision. */
    val isAwayFromStudio: Boolean get() = isOffsite || kind.isInherentlyOffsite

    /**
     * Whether this counts towards the rule at all.
     *
     * A card that has not been copied off is the original, not a copy of it, and counting
     * it would let a studio believe it had a backup when it had one card.
     */
    val isRealCopy: Boolean get() = kind != StorageKind.CameraCard
}
