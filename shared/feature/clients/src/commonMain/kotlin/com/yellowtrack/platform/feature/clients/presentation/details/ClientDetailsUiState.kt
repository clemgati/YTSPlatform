package com.yellowtrack.platform.feature.clients.presentation.details

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientDetailsModel

internal data class ClientDetailsUiState(
    val client: UiState<ClientDetailsModel>,
    /** What the studio charges in, for the booking form's contract value. */
    val currency: CurrencyCode = CurrencyCode.USD,
    /**
     * The account has gone, so this screen is about to be about nothing.
     *
     * Carried in the state rather than raised as an event because it is a fact about the
     * record and not a moment: a screen recomposing after the removal has to keep saying
     * so, or it would announce it once and then sit on an empty client for ever.
     */
    val removed: Boolean = false,
)
