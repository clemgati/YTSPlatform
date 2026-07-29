package com.yellowtrack.platform.feature.clients.presentation.details.model

import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine

/**
 * What the booking form collected.
 *
 * A booking, not a shoot day. A wedding is one booking containing an engagement shoot and
 * the wedding day; a commercial job contains a scout, a shoot, and a pickup. This is the
 * unit that carries one contract, one set of invoices, and one answer to "did that job
 * make money" — see `Project`.
 *
 * [contractValue] stays as text; parsing is the ViewModel's job, so what counts as a valid
 * amount is decided in one place.
 */
internal data class NewProject(
    val name: String,
    val serviceLine: ServiceLine,
    val status: ProjectStatus,
    val contractValue: String,
    val notes: String,
)
