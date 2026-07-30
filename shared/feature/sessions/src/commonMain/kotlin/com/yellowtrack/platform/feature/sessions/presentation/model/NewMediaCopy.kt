package com.yellowtrack.platform.feature.sessions.presentation.model

import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolumeId

/** What the copy form collected. */
internal data class NewMediaCopy(
    /**
     * The drive in the studio's register, when one was picked.
     *
     * Null when the studio typed a name instead. Building a register is not made a
     * precondition of recording a backup — the copy exists whether or not the drive has
     * been catalogued, and refusing to record it would lose the more important fact.
     */
    val volumeId: StorageVolumeId? = null,
    val volumeName: String,
    val kind: StorageKind,
    val isOffsite: Boolean,
)

/** A drive the copy could be on, as the form offers it. */
internal data class VolumeOption(
    val id: StorageVolumeId?,
    val label: String,
    val kind: StorageKind,
    val isOffsite: Boolean,
)
