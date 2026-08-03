package com.yellowtrack.platform.feature.clients.presentation.details.model

import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.feature.clients.presentation.model.NewClient

internal data class ClientDetailsModel(
    val id: ClientId,
    val displayName: String,
    val initials: String,
    val tags: List<String>,
    val contact: ClientContact,
    val upcomingSession: ClientUpcomingSession?,
    val sessionHistory: List<ClientSessionHistoryItem>,
    /** Every booking on this account, newest enquiry first. */
    val bookings: List<BookingSummary>,
    val notes: List<String>,
    /** Whether this account can be removed, and what is holding it if not. */
    val removal: Removal,
    /**
     * The account as the form takes it, so editing opens showing what is already there.
     *
     * Carried alongside the display model rather than rebuilt from it: the strings above
     * are formatted for reading — initials, joined notes — and parsing them back would be
     * recovering a value from its own presentation.
     */
    val editable: NewClient,
)
