package com.yellowtrack.platform.feature.sessions.presentation.details

import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.details.model.SessionDetailsModel
import com.yellowtrack.platform.feature.sessions.presentation.model.BookingOption
import com.yellowtrack.platform.feature.sessions.presentation.model.VolumeOption
import kotlinx.datetime.LocalDate

internal data class SessionDetailsUiState(
    val session: UiState<SessionDetailsModel>,
    /** Bookings this session could be moved between, for the edit form. */
    val bookings: List<BookingOption> = emptyList(),
    val today: LocalDate? = null,
    /** Drives from the studio's register, for the copy form. */
    val volumes: List<VolumeOption> = emptyList(),
    /** What the last drive read found. */
    val checkResult: String? = null,
    /** False on the web, which has no filesystem to read. */
    val canReadDrives: Boolean = false,
    /** True where the platform has a share sheet to hand a document to. */
    val canSendDocuments: Boolean = false,
)
