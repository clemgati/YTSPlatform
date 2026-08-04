package com.yellowtrack.platform.feature.dashboard.presentation.model

import com.yellowtrack.platform.core.model.client.ClientId

internal data class DashboardClient(
    /** So the row can open the account it names. */
    val id: ClientId,
    val name: String,
)
