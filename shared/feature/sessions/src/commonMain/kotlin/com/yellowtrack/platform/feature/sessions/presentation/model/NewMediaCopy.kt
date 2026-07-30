package com.yellowtrack.platform.feature.sessions.presentation.model

import com.yellowtrack.platform.core.model.media.StorageKind

/** What the copy form collected. */
internal data class NewMediaCopy(
    val volumeName: String,
    val kind: StorageKind,
    val isOffsite: Boolean,
)
