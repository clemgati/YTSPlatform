package com.yellowtrack.platform.core.model.lead

import kotlinx.serialization.Serializable

/**
 * Where an enquiry came from.
 *
 * Attribution is the whole point of this enum. Without it a studio cannot tell which
 * marketing effort produced paid work and which merely produced activity, and most
 * photographers spend years guessing — usually over-investing in the channel that feels
 * busiest rather than the one that books.
 */
@Serializable
enum class LeadSource {
    Instagram,
    TikTok,
    Website,
    GoogleSearch,

    /** A past client sending someone new. Usually the highest-converting source there is. */
    ClientReferral,

    /** A planner, venue, florist, or other supplier. The relationship worth cultivating. */
    VendorReferral,

    /** The client has booked before. Cheapest revenue a studio has. */
    RepeatClient,

    /** Wedding directories and paid listings — the ones worth auditing against their fee. */
    Directory,

    WalkIn,
    Other,
}
