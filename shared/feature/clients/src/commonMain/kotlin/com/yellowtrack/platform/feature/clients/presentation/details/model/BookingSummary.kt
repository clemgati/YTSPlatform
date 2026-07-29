package com.yellowtrack.platform.feature.clients.presentation.details.model

import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus

/** A booking as the client's page lists it. */
internal data class BookingSummary(
    val id: ProjectId,
    val name: String,
    val serviceLine: String,
    val status: ProjectStatus,
    /** Null until a figure has been agreed. */
    val value: String?,
    val bookedLabel: String?,
    /** The booking as the form takes it, so editing opens showing what is already there. */
    val editable: NewProject,
) {
    /** Whether this booking represents studio time actually held. */
    val isHeld: Boolean get() = status.isCommitted
}
