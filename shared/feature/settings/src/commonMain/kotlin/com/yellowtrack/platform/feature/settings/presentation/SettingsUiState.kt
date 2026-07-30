package com.yellowtrack.platform.feature.settings.presentation

import com.yellowtrack.platform.core.common.money.CurrencyCode
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
)
