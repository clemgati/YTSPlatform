package com.yellowtrack.platform.core.model.lead

/**
 * What became of a studio's enquiries.
 *
 * Three numbers rather than two, and that is the whole design of it. "Of 200 enquiries, 150
 * became clients and 50 did not" is arithmetic that only works once every enquiry has
 * finished, and a studio always has some in flight. Counting those as failures makes the rate
 * look worse than it is and — worse — makes it *move* every time an enquiry arrives, which is
 * the opposite of what a rate is for.
 *
 * So [converted] over [settled], and the ones still open are reported beside it rather than
 * folded into either side.
 */
data class EnquiryOutcomes(
    /** Every enquiry the studio has, whatever became of it. */
    val total: Int,
    /** Enquiries that produced a client. */
    val converted: Int,
    /** Enquiries that went elsewhere or went quiet. */
    val lost: Int,
) {
    /** Neither won nor lost yet. Not a failure, and not evidence of anything. */
    val open: Int get() = total - converted - lost

    /**
     * Enquiries whose outcome is known.
     *
     * The denominator, because an enquiry that arrived this morning has not failed.
     */
    val settled: Int get() = converted + lost

    /**
     * Converted as a percentage of settled, or null when nothing has settled.
     *
     * Null rather than zero. A studio with three open enquiries and no outcomes has not
     * converted 0% of anything, and showing it that number would be telling it something
     * untrue on its first week.
     */
    val conversionRate: Int? get() = if (settled == 0) null else (converted * PERCENT) / settled

    val hasAny: Boolean get() = total > 0

    private companion object {
        const val PERCENT = 100
    }
}

/**
 * Counts what became of these enquiries.
 *
 * Converted is measured by [Lead.convertedClientId] rather than by [LeadStatus.Won], because
 * the link is the fact and the status is a label somebody can set. A studio that marks an
 * enquiry won by hand has told itself something; a studio that converted one has a client to
 * show for it.
 */
fun List<Lead>.outcomes(): EnquiryOutcomes =
    EnquiryOutcomes(
        total = size,
        converted = count { it.convertedClientId != null },
        // A won enquiry with no client is neither converted nor lost: it is a label without a
        // record behind it, and it stays in `open` where somebody may notice and finish it.
        lost = count { it.status == LeadStatus.Lost },
    )
