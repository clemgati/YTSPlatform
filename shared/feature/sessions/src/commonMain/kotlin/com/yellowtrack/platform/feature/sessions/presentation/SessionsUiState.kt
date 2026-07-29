package com.yellowtrack.platform.feature.sessions.presentation

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.model.BookingOption
import com.yellowtrack.platform.feature.sessions.presentation.model.SessionGroup
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

internal data class SessionsUiState(
    val groups: UiState<List<SessionGroup>>,
    val totalCount: Int = 0,
    /** Bookings a session can be scheduled inside. Empty until a client has one. */
    val bookings: List<BookingOption> = emptyList(),
    val today: LocalDate? = null,
    /**
     * The zone a new session is entered in.
     *
     * Carried from the ViewModel rather than read again in the form, so the instants the
     * form reasons about and the ones that get stored are resolved against the same zone.
     */
    val zone: TimeZone = TimeZone.currentSystemDefault(),
)
