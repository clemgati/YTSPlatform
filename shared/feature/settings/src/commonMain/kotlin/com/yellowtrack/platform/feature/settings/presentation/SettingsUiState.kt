package com.yellowtrack.platform.feature.settings.presentation

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.data.sync.ConflictDifference
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import com.yellowtrack.platform.core.ui.state.UiState

internal data class SettingsUiState(
    val content: UiState<SettingsContent>,
)

/**
 * The studio's details, as text, so the form opens showing what is already there.
 *
 * Held as strings rather than as the domain object because a half-typed form is not a
 * valid profile, and forcing it through the model on every keystroke would mean either
 * rejecting the keystroke or storing something that fails its own rules.
 */
internal data class StudioProfileFields(
    val name: String = "",
    val address: String = "",
    val email: String = "",
    val phone: String = "",
    val website: String = "",
    val taxNumber: String = "",
    val paymentInstructions: String = "",
    val documentFooter: String = "",
    val currency: CurrencyCode = CurrencyCode.USD,
)

internal data class SettingsContent(
    val profile: StudioProfileFields,
    /** True once a name has been saved, which is what documents wait on. */
    val canIssueDocuments: Boolean,
    /** What a client will notice is missing from an invoice. */
    val gaps: List<String>,
    val savedNote: String?,
    /** Work synchronisation discarded, oldest first. */
    val conflicts: List<ConflictSummary>,
    val sync: SyncSummary,
)

/**
 * Whether synchronisation is actually working, in a form a studio can read.
 *
 * Worth showing at all because the failure it guards against is silence: a device that
 * stopped reconciling looks exactly like one with nothing to reconcile, and the difference
 * only becomes visible when somebody opens a booking on the other device and it is not
 * there.
 */
internal data class SyncSummary(
    val isWorking: Boolean = false,
    val lastResult: String? = null,
    val isFailure: Boolean = false,
)

/**
 * One clash between two devices, as the studio needs to read it.
 *
 * ADR 0008 chose last-write-wins on the condition that the version it discarded stays
 * recoverable by whoever wrote it. This is the shape that promise takes on a screen: what
 * it was, when it happened, and which fields actually moved.
 */
internal data class ConflictSummary(
    val id: SyncConflictId,
    /** "A shoot day", not "session" — the studio never chose the table names. */
    val what: String,
    val whenDetected: String,
    val differences: List<ConflictDifference>,
) {
    /**
     * True when the payload could not be read into fields.
     *
     * The conflict is still shown. A version that cannot be rendered is still a version
     * that was thrown away, and hiding it would be the failure this whole table exists to
     * prevent.
     */
    val isUnreadable: Boolean get() = differences.isEmpty()
}
