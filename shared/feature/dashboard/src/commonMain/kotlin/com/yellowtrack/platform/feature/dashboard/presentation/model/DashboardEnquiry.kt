package com.yellowtrack.platform.feature.dashboard.presentation.model

import com.yellowtrack.platform.core.model.lead.LeadId

/**
 * An enquiry that has not yet been replied to.
 *
 * Given a section of its own on the dashboard because response time is the strongest
 * predictor of whether an enquiry books, and because an unanswered message is the one
 * thing in this business that gets worse purely through the passage of time.
 */
internal data class DashboardEnquiry(
    val id: LeadId,
    val name: String,
    val source: String,
    val waitingLabel: String,
    /** Set once a reply is late enough to be costing bookings. */
    val isUrgent: Boolean,
)
