package com.yellowtrack.platform.feature.dashboard.presentation.model

import com.yellowtrack.platform.core.model.session.SessionId

internal data class DashboardSession(
    /** So the row can open the day it names. */
    val id: SessionId,
    val clientName: String,
    val title: String,
    val time: String,
)
