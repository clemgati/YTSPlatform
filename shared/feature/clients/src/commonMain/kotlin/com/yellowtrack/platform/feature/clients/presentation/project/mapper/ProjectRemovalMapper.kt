package com.yellowtrack.platform.feature.clients.presentation.project.mapper

import com.yellowtrack.platform.core.ui.removal.Removal
import com.yellowtrack.platform.core.ui.removal.heldBy

/**
 * Works out what is holding a booking in place.
 *
 * A booking is what everything else hangs off. Eight kinds of record point at one, and six
 * of them cannot exist without it — their `projectId` is not nullable. Removing a booking
 * and taking its invoices and payments along would delete the record of money that actually
 * changed hands, so nothing cascades and anything attached at all is enough to stop it.
 *
 * Money is listed first. When several kinds are attached, the first one read is the one a
 * studio weighs the decision against, and an invoice is a heavier reason to stop than a
 * post-production task.
 *
 * The names are the studio's rather than the tables': a photographer has shoot days and
 * costs, not sessions and expenses.
 */
internal fun projectRemoval(
    invoices: Int,
    quotes: Int,
    contracts: Int,
    costs: Int,
    journeys: Int,
    shootDays: Int,
    deliverables: Int,
    postProductionTasks: Int,
): Removal =
    heldBy(
        Removal.Hold("invoice", "invoices", invoices),
        Removal.Hold("quote", "quotes", quotes),
        Removal.Hold("contract", "contracts", contracts),
        Removal.Hold("cost", "costs", costs),
        Removal.Hold("journey", "journeys", journeys),
        Removal.Hold("shoot day", "shoot days", shootDays),
        Removal.Hold("deliverable", "deliverables", deliverables),
        Removal.Hold("post-production task", "post-production tasks", postProductionTasks),
    )
