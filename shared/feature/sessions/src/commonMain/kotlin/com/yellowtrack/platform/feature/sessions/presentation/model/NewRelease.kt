package com.yellowtrack.platform.feature.sessions.presentation.model

import com.yellowtrack.platform.core.model.release.ReleaseKind

/**
 * What the release form collected.
 *
 * A release is recorded as pending by default. Marking one signed is a separate,
 * deliberate act, because "I have their permission" is a claim about a piece of paper that
 * either exists or does not.
 */
internal data class NewRelease(
    val personName: String,
    val kind: ReleaseKind,
    val email: String,
    val guardianName: String,
)
