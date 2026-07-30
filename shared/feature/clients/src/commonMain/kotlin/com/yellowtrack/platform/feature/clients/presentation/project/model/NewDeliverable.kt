package com.yellowtrack.platform.feature.clients.presentation.project.model

import com.yellowtrack.platform.core.model.delivery.DeliverableKind

/**
 * What the deliverable form collected.
 *
 * No due date is asked for. It is normally the shoot date plus the turnaround the contract
 * already promises, and asking a studio to work that out by hand is asking it to get its
 * own deadline wrong.
 */
internal data class NewDeliverable(
    val name: String,
    val kind: DeliverableKind,
)
