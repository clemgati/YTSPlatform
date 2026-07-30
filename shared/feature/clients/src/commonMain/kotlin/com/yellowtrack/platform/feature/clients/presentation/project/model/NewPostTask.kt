package com.yellowtrack.platform.feature.clients.presentation.project.model

import com.yellowtrack.platform.core.model.post.PostTaskKind

/**
 * What the post-production form collected.
 *
 * The estimate is asked for when the work is added, not when it is finished: an estimate
 * written afterwards is a memory of how long it felt, and it agrees with the actual every
 * time.
 */
internal data class NewPostTask(
    val name: String,
    val kind: PostTaskKind,
    val estimatedHours: String,
)
