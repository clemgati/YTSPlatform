package com.yellowtrack.platform.core.model.media

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class StorageVolumeId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): StorageVolumeId = StorageVolumeId(uuidV7().toString())
    }
}

/** Whether a drive can still be relied on. */
@Serializable
enum class VolumeStatus {
    InUse,

    /**
     * Dead, or dying.
     *
     * The state the whole register exists for: the question a studio asks the moment a
     * drive fails is which shoots were on it and which of them are now down to one copy.
     */
    Failed,

    /** Deliberately taken out of service. What was on it was moved off first. */
    Retired,

    /** Missing. Its contents are somewhere, in someone else's hands. */
    Lost,
    ;

    /** Whether copies on this drive still count towards the 3-2-1 rule. */
    val holdsFiles: Boolean get() = this == InUse
}

/**
 * One place the studio keeps files — a drive, a NAS, a cloud bucket.
 *
 * Copies have carried a free-text `volumeName` since 0.6.0, which meant "Red Samsung T7"
 * typed on twelve shoots was twelve unrelated strings. A studio could ask whether one
 * wedding was safe and could not ask the question that actually gets asked: *this drive
 * has died — what was on it, and what am I now down to one copy of?*
 *
 * Deliberately not a `GearItem` with [GearCategory.Storage], even though a drive is also a
 * thing the studio owns and insures. The questions asked of a volume — what is on it, is
 * it in the building, when did anyone last confirm it reads — have nothing to do with
 * packing it into a van, and joining the two would make each answer harder to get.
 *
 * @param isOffsite where this drive normally lives, which is a property of the drive
 *   rather than of any one copy on it. A drive kept at a relative's house is offsite for
 *   every shoot on it, and asking the studio to remember that per copy is asking it to get
 *   it wrong.
 * @param lastCheckedAt when someone last confirmed the drive still reads. A drive can fail
 *   silently, and the studio finds out on the day it is needed.
 */
@Serializable
data class StorageVolume(
    val id: StorageVolumeId,
    override val studioId: StudioId,
    /** What the studio calls it — "Red Samsung T7", "Studio NAS", "Backblaze". */
    val label: String,
    val kind: StorageKind,
    val status: VolumeStatus = VolumeStatus.InUse,
    val isOffsite: Boolean = false,
    val lastCheckedAt: Instant? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    /** Cloud and offsite drives are away by definition; anything else is a studio decision. */
    val isAwayFromStudio: Boolean get() = isOffsite || kind.isInherentlyOffsite

    /** Whether files on this drive can still be counted on. */
    val isDependable: Boolean get() = status.holdsFiles
}
