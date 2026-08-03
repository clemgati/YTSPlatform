package com.yellowtrack.platform.core.ui.removal

/**
 * Whether a record can be removed, and what is holding it if not.
 *
 * Three screens ask this question — a client is held by its bookings, a booking by
 * everything hanging off it, a shoot day by what was recorded on it — and they all have to
 * answer it in the same words. The rule is the same each time: nothing cascades, because
 * removing a record and silently taking its children along destroys work nobody asked to
 * lose, and what is in the way gets named rather than merely refused.
 *
 * Lives here rather than in each feature because the wording is the part that would drift.
 * Three copies of a pluralisation rule become three slightly different sentences, and a
 * studio meeting "1 shoot days" on one screen trusts the rest of them less.
 */
sealed interface Removal {
    /** Nothing is attached — the record entered by mistake, before anything was put on it. */
    data object Available : Removal

    /**
     * Held, with each attachment counted and named as a studio would say it.
     *
     * The holds are carried rather than a finished sentence so a test can assert what is
     * holding a record without matching prose, and so a screen can phrase the lead-in
     * itself.
     */
    data class HeldBy(
        val holds: List<Hold>,
    ) : Removal {
        /** "2 invoices, 1 shoot day and 3 post-production tasks". */
        val summary: String
            get() =
                holds.map(Hold::label).let { labels ->
                    when (labels.size) {
                        1 -> labels.single()
                        else -> labels.dropLast(1).joinToString(", ") + " and " + labels.last()
                    }
                }
    }

    /**
     * One kind of thing attached to the record, and how many of them there are.
     *
     * Both wordings are given rather than an "s" being appended, because the things being
     * counted are named as a photographer names them and not every one of those pluralises
     * by suffix.
     */
    data class Hold(
        val singular: String,
        val plural: String,
        val count: Int,
    ) {
        val label: String get() = "$count ${if (count == 1) singular else plural}"
    }
}

/**
 * Builds the answer from counts, dropping whatever is not there.
 *
 * Counts rather than the records themselves, so this stays a pure function of a few numbers
 * and a test can put a record in any state without building the domain objects behind it.
 *
 * Order is the caller's, and it carries meaning: the first thing read is what the decision
 * gets weighed against, so money is passed first where there is money.
 */
fun heldBy(vararg holds: Removal.Hold): Removal {
    val present = holds.filter { it.count > 0 }

    return when {
        present.isEmpty() -> Removal.Available
        else -> Removal.HeldBy(present)
    }
}
