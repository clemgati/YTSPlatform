package com.yellowtrack.platform.feature.clients.presentation.details.model

import com.yellowtrack.platform.core.model.client.ClientId

internal data class ClientDetailsModel(
    val id: ClientId,
    val displayName: String,
    val initials: String,
    val tags: List<String>,
    val contact: ClientContact,
    val upcomingSession: ClientUpcomingSession?,
    val sessionHistory: List<ClientSessionHistoryItem>,
    val notes: List<String>,
)
