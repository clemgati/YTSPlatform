package com.yellowtrack.platform.core.model.lead

import kotlinx.serialization.Serializable

/** Where an enquiry stands in the booking funnel. */
@Serializable
enum class LeadStatus {
    /** Arrived, not yet replied to. The clock on response time is running. */
    New,

    Contacted,
    ConsultScheduled,
    ProposalSent,

    /** Converted into a project. */
    Won,

    /** Went elsewhere, or went quiet. */
    Lost,
    ;

    val isOpen: Boolean get() = this !in setOf(Won, Lost)

    /** Counts toward conversion rate: a lead that was never replied to still counts. */
    val isResolved: Boolean get() = !isOpen
}
