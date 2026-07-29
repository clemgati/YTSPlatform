package com.yellowtrack.platform.feature.sessions.presentation.details

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.details.model.SessionDetailsModel
import com.yellowtrack.platform.feature.sessions.presentation.model.BookingOption
import kotlinx.datetime.LocalDate

internal data class SessionDetailsUiState(
    val session: UiState<SessionDetailsModel>,
    /** Bookings this session could be moved between, for the edit form. */
    val bookings: List<BookingOption> = emptyList(),
    val today: LocalDate? = null,
)
