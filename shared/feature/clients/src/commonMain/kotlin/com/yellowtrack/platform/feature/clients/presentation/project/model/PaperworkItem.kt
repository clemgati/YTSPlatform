package com.yellowtrack.platform.feature.clients.presentation.project.model

/**
 * A quote or a contract on this booking, in whatever state it reached.
 *
 * These had nowhere to be seen once they stopped needing an answer. The Ledger lists quotes
 * only while they are awaiting a decision and contracts only until they hold their date, so
 * an accepted quote and a signed-and-paid contract both vanished from the application the
 * moment they succeeded.
 *
 * That was survivable while nothing depended on reaching them. It stopped being survivable
 * when a booking started refusing to be removed because of them: the studio was told a
 * quote was in the way and had no screen anywhere that would show it. Listing them on the
 * booking they belong to fixes both — the paperwork sits with its job, and the thing named
 * in the refusal is on the same page as the refusal.
 */
internal data class PaperworkItem(
    val id: String,
    val kind: Kind,
    /** "Quote 2026-014", or the contract's own title. */
    val title: String,
    /** Where it got to: "Accepted", "Signed", "Declined". */
    val statusLabel: String,
    /** The quote's total, or the contract's retainer where it has one. */
    val amount: String?,
) {
    enum class Kind {
        Quote,
        Contract,
    }
}
