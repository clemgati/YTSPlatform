package com.yellowtrack.platform.feature.dashboard.presentation.model

internal data class DashboardSummary(
    val todaysSessions: List<DashboardSession>,
    val recentClients: List<DashboardClient>,
    val studioStatus: DashboardStudioStatus,
    val enquiriesAwaitingReply: List<DashboardEnquiry> = emptyList(),
    /**
     * Every enquiry, whatever became of it.
     *
     * The awaiting-reply list above holds only what has never been answered, so replying to
     * an enquiry — or marking it won or lost — took it off the one screen that showed leads
     * at all. A spam message somebody answered, or a duplicate, could then never be removed.
     */
    val allEnquiries: List<DashboardEnquiry> = emptyList(),
    /**
     * What became of them. Null until the studio has any, because "0 of 0" on a first run
     * reads as a measurement rather than an absence.
     */
    val outcomes: EnquiryOutcomesSummary? = null,
    val todayLabel: String = "",
)
