package com.yellowtrack.platform.feature.dashboard.presentation.model

internal data class DashboardSummary(
    val todaysSessions: List<DashboardSession>,
    val recentClients: List<DashboardClient>,
    val studioStatus: DashboardStudioStatus,
)
