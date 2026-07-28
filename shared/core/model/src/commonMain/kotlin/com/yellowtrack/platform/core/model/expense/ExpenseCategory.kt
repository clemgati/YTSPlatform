package com.yellowtrack.platform.core.model.expense

import kotlinx.serialization.Serializable

/**
 * What an expense was for.
 *
 * [isTypicallyOverhead] drives a sensible default when recording, not a rule: insurance
 * is normally an annual overhead, but a one-off policy rider for a single commercial
 * shoot is a cost of that job. The studio decides; this only saves a tap.
 */
@Serializable
enum class ExpenseCategory(
    val isTypicallyOverhead: Boolean,
) {
    /** Bodies, lenses, lighting. Capital, but it still has to be earned back. */
    Gear(isTypicallyOverhead = true),

    /** Rented for a specific job far more often than not. */
    GearRental(isTypicallyOverhead = false),

    GearMaintenance(isTypicallyOverhead = true),
    Software(isTypicallyOverhead = true),
    Insurance(isTypicallyOverhead = true),
    StudioRent(isTypicallyOverhead = true),
    Marketing(isTypicallyOverhead = true),
    Education(isTypicallyOverhead = true),
    Accounting(isTypicallyOverhead = true),
    BankAndProcessingFees(isTypicallyOverhead = true),

    /** Paid per shoot, and the largest job cost most studios carry. */
    SecondShooter(isTypicallyOverhead = false),

    /** Outsourced culling, editing, or colour. */
    Contractor(isTypicallyOverhead = false),

    Travel(isTypicallyOverhead = false),
    Accommodation(isTypicallyOverhead = false),
    Meals(isTypicallyOverhead = false),

    /** Location fees, and the permits that stop a commercial shoot being shut down. */
    Permits(isTypicallyOverhead = false),

    Props(isTypicallyOverhead = false),

    /** Albums, prints, and packaging — a cost of delivering a specific job. */
    PrintsAndAlbums(isTypicallyOverhead = false),

    Other(isTypicallyOverhead = true),
}
