package com.yellowtrack.platform.feature.ledger.presentation

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.ledger.presentation.model.ExpenseSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.MoneyOwedSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.PricingSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import com.yellowtrack.platform.feature.ledger.presentation.model.ProposalsSummary
import kotlinx.datetime.LocalDate

internal data class LedgerUiState(
    val content: UiState<LedgerContent>,
)

internal data class LedgerContent(
    val moneyOwed: MoneyOwedSummary,
    /** Quotes and contracts still out with clients. */
    val proposals: ProposalsSummary,
    /** Null until the studio has stated its pricing basis. */
    val pricing: PricingSummary?,
    val expenses: ExpenseSummary,
    /** Bookings a cost can be charged to. */
    val projects: List<ProjectOption>,
    val today: LocalDate,
    val currency: CurrencyCode,
    val pricingBasis: PricingBasisFields,
)

/** The saved pricing inputs, as text, so the form opens showing what is already there. */
internal data class PricingBasisFields(
    val salary: String = "",
    val billableDays: String = "",
    val taxRate: String = "",
    val currency: CurrencyCode = CurrencyCode.USD,
)
