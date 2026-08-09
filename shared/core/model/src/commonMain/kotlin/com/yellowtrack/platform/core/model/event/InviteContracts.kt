package com.yellowtrack.platform.core.model.event

import kotlinx.serialization.Serializable

/**
 * What somebody scanning a code is told about the event.
 *
 * One field, and that is the design rather than a starting point — a public endpoint returns
 * what the page needs to say and not a row.
 */
@Serializable
data class InvitedEventResponse(
    val eventName: String,
)

@Serializable
data class SignUpToEventRequest(
    val email: String,
    val name: String? = null,
)

/** The studio's own view of an event's invite: the token, and where it points. */
@Serializable
data class EventInviteResponse(
    val token: String,
    /**
     * The address a QR code should encode.
     *
     * Built by the server rather than the client, so the printed banner and the route that
     * honours it cannot disagree — and so the public site's address lives in one place.
     */
    val url: String,
)
