package com.yellowtrack.platform.core.model.client

import kotlinx.serialization.Serializable

/**
 * The part a person plays in a client account.
 *
 * The distinction is operational, not cosmetic: [Billing] receives the invoice,
 * [OnSite] is who you call when you arrive and nobody answers the door, and [Planner]
 * is frequently the only person who actually replies before a wedding.
 */
@Serializable
enum class ClientContactRole {
    /** The decision-maker and default recipient of correspondence. */
    Primary,

    /** The second half of a couple. Equal standing to [Primary]. */
    Partner,

    /** Wedding or event planner acting on the client's behalf. */
    Planner,

    /** Accounts payable. Receives invoices; usually not involved in the shoot. */
    Billing,

    /** Creative director or brand lead who approves the work. */
    Creative,

    /** On-site day-of contact — venue coordinator, property manager, location scout. */
    OnSite,

    /** Assistant or gatekeeper who schedules on the client's behalf. */
    Assistant,
}
