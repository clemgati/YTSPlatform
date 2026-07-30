package com.yellowtrack.platform.feature.clients.presentation.details

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientDetailsModel

internal data class ClientDetailsUiState(
    val client: UiState<ClientDetailsModel>,
    /** What the studio charges in, for the booking form's contract value. */
    val currency: CurrencyCode = CurrencyCode.USD,
)
