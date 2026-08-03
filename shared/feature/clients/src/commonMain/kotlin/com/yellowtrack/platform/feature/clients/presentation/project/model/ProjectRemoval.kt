package com.yellowtrack.platform.feature.clients.presentation.project.model

/**
 * Whether this booking can be removed, and what is holding it if not.
 *
 * A booking is the opposite shape to a client. A client is held by its bookings and holds
 * nothing itself, so the rule there is one question. Eight things point at a booking —
 * shoot days, invoices, quotes, contracts, costs, journeys, deliverables and
 * post-production tasks — and six of them cannot exist without one, because their
 * `projectId` is not nullable. It is the thing the money hangs off.
 *
 * So nothing cascades. Removing a booking and taking its invoices and payments with it
 * would be deleting the record of money that actually changed hands, and the studio would
 * be left with a bank balance it could no longer account for. What is attached is named
 * instead, because "you cannot" is only useful next to "here is what is stopping you".
 */
internal sealed interface ProjectRemoval {
    /** Nothing is attached — the booking entered by mistake, before anything was put on it. */
    data object Available : ProjectRemoval

    /**
     * Held, with each attachment counted and named as a studio would say it.
     *
     * The counts are carried rather than a ready-made sentence so the caller can decide
     * how to say it, and so a test can assert what is holding a booking without matching
     * prose.
     */
    data class HeldBy(
        val holds: List<Hold>,
    ) : ProjectRemoval {
        /** "2 shoot days and 1 invoice" — what goes into the line under the control. */
        val summary: String
            get() =
                holds.map(Hold::label).let { labels ->
                    when (labels.size) {
                        1 -> labels.single()
                        else -> labels.dropLast(1).joinToString(", ") + " and " + labels.last()
                    }
                }
    }

    /** One kind of thing attached to the booking, and how many of them there are. */
    data class Hold(
        val kind: Kind,
        val count: Int,
    ) {
        val label: String get() = "$count ${if (count == 1) kind.singular else kind.plural}"
    }

    /**
     * Named as the studio names them rather than as the tables are named. A photographer
     * has shoot days and costs, not sessions and expenses.
     */
    enum class Kind(
        val singular: String,
        val plural: String,
    ) {
        Invoice("invoice", "invoices"),
        Quote("quote", "quotes"),
        Contract("contract", "contracts"),
        Cost("cost", "costs"),
        Journey("journey", "journeys"),
        ShootDay("shoot day", "shoot days"),
        Deliverable("deliverable", "deliverables"),
        PostProductionTask("post-production task", "post-production tasks"),
    }
}
