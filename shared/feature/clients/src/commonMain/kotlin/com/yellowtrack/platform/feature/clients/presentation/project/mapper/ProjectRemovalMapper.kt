package com.yellowtrack.platform.feature.clients.presentation.project.mapper

import com.yellowtrack.platform.feature.clients.presentation.project.model.ProjectRemoval

/**
 * Works out what is holding a booking in place, counting every kind of thing that points
 * at one.
 *
 * Counts rather than the records themselves, so this stays a pure function of eight
 * numbers and a test can put a booking in any state without building eight domain objects.
 *
 * Money is listed first. When several kinds are attached the first one read is the one a
 * studio weighs the decision against, and an invoice is a heavier reason to stop than a
 * post-production task.
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
): ProjectRemoval {
    val holds =
        listOf(
            ProjectRemoval.Kind.Invoice to invoices,
            ProjectRemoval.Kind.Quote to quotes,
            ProjectRemoval.Kind.Contract to contracts,
            ProjectRemoval.Kind.Cost to costs,
            ProjectRemoval.Kind.Journey to journeys,
            ProjectRemoval.Kind.ShootDay to shootDays,
            ProjectRemoval.Kind.Deliverable to deliverables,
            ProjectRemoval.Kind.PostProductionTask to postProductionTasks,
        ).filter { (_, count) -> count > 0 }
            .map { (kind, count) -> ProjectRemoval.Hold(kind = kind, count = count) }

    return when {
        holds.isEmpty() -> ProjectRemoval.Available
        else -> ProjectRemoval.HeldBy(holds)
    }
}
