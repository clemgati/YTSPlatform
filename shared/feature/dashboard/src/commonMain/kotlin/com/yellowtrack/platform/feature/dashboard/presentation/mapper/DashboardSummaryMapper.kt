package com.yellowtrack.platform.feature.dashboard.presentation.mapper

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardClient
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSession
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardStudioStatus
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardSummary

internal fun toDashboardSummary(
    todaysSessions: List<DashboardSession>,
    recentClients: List<Client>,
    studioStatus: DashboardStudioStatus,
): DashboardSummary =
    DashboardSummary(
        todaysSessions = todaysSessions,
        recentClients =
            recentClients
                .take(MAX_RECENT_CLIENTS)
                .map(Client::toDashboardClient),
        studioStatus = studioStatus,
    )

private fun Client.toDashboardClient(): DashboardClient =
    DashboardClient(
        name = displayName,
    )

private const val MAX_RECENT_CLIENTS = 3
