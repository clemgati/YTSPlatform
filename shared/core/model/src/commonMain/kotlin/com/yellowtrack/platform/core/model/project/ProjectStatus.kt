package com.yellowtrack.platform.core.model.project

import kotlinx.serialization.Serializable

/** Where a booking stands, from first enquiry to archived. */
@Serializable
enum class ProjectStatus {
    /** An enquiry that has not yet been quoted. */
    Enquiry,

    /** A proposal has been sent and is awaiting a decision. */
    Proposed,

    /** Contract signed and retainer paid. This is the point at which a date is held. */
    Booked,

    /** Shooting has begun. */
    Shooting,

    /** Shooting complete; culling, editing, and colour in progress. */
    InPost,

    /** Gallery or files delivered to the client. */
    Delivered,

    /** Delivered, paid in full, and archived. */
    Complete,

    /** Cancelled after booking. Retainer treatment depends on the contract. */
    Cancelled,

    /** Never booked — the enquiry went elsewhere or went quiet. */
    Lost,
    ;

    val isActive: Boolean
        get() = this in setOf(Enquiry, Proposed, Booked, Shooting, InPost, Delivered)

    /** Statuses that represent held studio time, and therefore a real calendar commitment. */
    val isCommitted: Boolean
        get() = this in setOf(Booked, Shooting, InPost, Delivered)
}
