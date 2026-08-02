package com.yellowtrack.platform.feature.dashboard.presentation.model

internal data class DashboardSummary(
    val todaysSessions: List<DashboardSession>,
    val recentClients: List<DashboardClient>,
    val studioStatus: DashboardStudioStatus,
    val enquiriesAwaitingReply: List<DashboardEnquiry> = emptyList(),
    val todayLabel: String = "",
    /**
     * How much work synchronisation has discarded and nobody has looked at.
     *
     * A count rather than the conflicts themselves: the Dashboard's job is to make sure
     * the studio finds out, and Settings' job is to show them what was lost. ADR 0008
     * decision 3 required both — a conflict only visible to somebody who happens to open
     * Settings is not really shown.
     */
    val unresolvedConflicts: Int = 0,
)
