package com.yellowtrack.platform.core.model.event

import kotlinx.serialization.Serializable

/**
 * What became of an uploaded photograph.
 *
 * Here rather than beside the route so the watcher on a photographer's laptop and the server
 * that answers it are compiled against one definition, in the manner of ADR 0007. The
 * alternative is two records that agree by inspection until one of them is edited.
 */
@Serializable
data class PhotographAccepted(
    val photoId: String,
    /** The registration it belongs to, or null when it belongs to the event's gallery. */
    val registrationId: String? = null,
)
