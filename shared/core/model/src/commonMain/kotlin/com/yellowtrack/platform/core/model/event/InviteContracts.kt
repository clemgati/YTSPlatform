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
    /**
     * Both parts, and both required.
     *
     * A single name field produced "John Smith" twice at one event and no way to tell the two
     * apart. Optional produced rows with an address and nothing else, which is worse: the
     * photographer seating people has a queue in front of them and a list that does not say
     * who is in it.
     */
    val givenName: String,
    val familyName: String,
    /**
     * Optional, and not used to send anything yet.
     *
     * Given so a guest can be sent their link by message later. Until then it is the other
     * thing that tells two people of the same name apart, and only the studio ever sees it.
     */
    val phone: String? = null,
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

/** What a delivery did, as the studio's screen reads it. */
@Serializable
data class DeliveredResponse(
    val email: String,
    val photographs: Int,
    /** False when the sitting had already been handed over and nothing was sent again. */
    val sentNow: Boolean,
)

/** Somebody's own photographs. */
@Serializable
data class GalleryResponse(
    val eventName: String,
    /** Temporary URLs, oldest first. They expire; the gallery link does not. */
    val photographs: List<String>,
)

/**
 * A sign-up code as geometry, for a client that draws it itself.
 *
 * The printed card gets SVG, which a browser renders. An application showing the code on a
 * screen has no browser and no SVG renderer, and adding one to every platform to draw a grid
 * of squares would be absurd — so the server sends the grid and the client draws it.
 *
 * Encoding a QR code is Reed-Solomon and masking, which belongs in one place. Drawing one is
 * a loop.
 */
@Serializable
data class QrMatrix(
    /** Width and height in modules, including the quiet zone. */
    val size: Int,
    /**
     * One string per row, `1` for a dark module.
     *
     * Text rather than a packed encoding because it is a few kilobytes either way, and this
     * one can be read in a log when somebody asks why a code will not scan.
     */
    val rows: List<String>,
)
