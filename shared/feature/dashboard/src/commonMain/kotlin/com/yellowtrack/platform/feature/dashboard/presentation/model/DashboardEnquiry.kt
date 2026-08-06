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
    /**
     * Where it got to — "Replied", "Won", "Lost".
     *
     * Only meaningful in the full list. The awaiting-reply list is by definition all of one
     * status, and saying so on every row would be noise.
     */
    val statusLabel: String = "",
    /** The enquiry as the form takes it, so correcting a mistyped name opens on it. */
    val editable: NewEnquiry? = null,
    /**
     * Whether turning it into a client is still on offer.
     *
     * False once it already produced one. The link is a fact about the past and converting
     * twice would make a second client for the same person, which is worse than no button.
     */
    val canConvert: Boolean = false,
    /** "Became a client" once it has, so the row says what happened rather than offering it again. */
    val convertedLabel: String? = null,
)

/**
 * What became of a studio's enquiries, as the dashboard says it.
 *
 * Three numbers rather than two: an enquiry still in flight is not a failure, and folding it
 * into one would make the rate move every time work arrived.
 */
internal data class EnquiryOutcomesSummary(
    val headline: String,
    val detail: String,
)
