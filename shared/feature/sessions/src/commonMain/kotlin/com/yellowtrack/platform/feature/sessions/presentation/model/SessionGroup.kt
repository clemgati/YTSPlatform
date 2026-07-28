package com.yellowtrack.platform.feature.sessions.presentation.model

/**
 * Sessions grouped under a heading.
 *
 * A photographer reads their schedule as "what's next", not as an undifferentiated list,
 * so upcoming and past work are separated rather than merely sorted.
 */
internal data class SessionGroup(
    val title: String,
    val sessions: List<SessionListItem>,
)
