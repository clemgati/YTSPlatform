package com.yellowtrack.platform.feature.ledger.presentation

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.ledger.presentation.model.ExpenseSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.MoneyOwedSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.PackagePricing
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
    /** What the studio sells. Present whether or not there is a floor to measure it against. */
    val packages: List<PackagePricing>,
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
    /**
     * Blank means "add up the expenses I have logged".
     *
     * The model has carried this since the calculator was written and nothing could set it,
     * so a studio without a full year of expenses got a floor built on a partial sum — too
     * low, which is the one direction that puts a business out of operation.
     */
    val annualOverhead: String = "",
    /** Blank means none. Retained on top of costs and pay, not part of them. */
    val profitMargin: String = "",
    val currency: CurrencyCode = CurrencyCode.USD,
)
