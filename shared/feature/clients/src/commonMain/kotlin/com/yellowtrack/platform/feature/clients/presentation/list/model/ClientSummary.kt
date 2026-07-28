package com.yellowtrack.platform.feature.clients.presentation.list.model

import com.yellowtrack.platform.core.model.client.ClientId

internal data class ClientSummary(
    val id: ClientId,
    val displayName: String,
    val initials: String,
    val sessionCount: Int,
    val lastSession: String?,
    val tags: List<String>,
)
