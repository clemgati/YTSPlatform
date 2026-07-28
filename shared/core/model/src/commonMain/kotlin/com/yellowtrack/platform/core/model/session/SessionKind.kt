package com.yellowtrack.platform.core.model.session

import kotlinx.serialization.Serializable

/** What a scheduled block is for. Not every session involves a camera. */
@Serializable
enum class SessionKind {
    /** Consultation or discovery call, before or just after booking. */
    Consultation,

    /** Location scout or technical recce. Billable on commercial work. */
    Scout,

    /** The shoot itself. */
    Shoot,

    /** Additional footage or stills gathered after the main shoot. */
    Pickup,

    /** In-person delivery, album reveal, or ordering appointment. */
    Delivery,
}
