package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.testing.FakeClientRepository
import com.yellowtrack.platform.core.testing.FakeCodbRepository
import com.yellowtrack.platform.core.testing.FakeContractRepository
import com.yellowtrack.platform.core.testing.FakeExpenseRepository
import com.yellowtrack.platform.core.testing.FakeInvoiceRepository
import com.yellowtrack.platform.core.testing.FakePostProductionRepository
import com.yellowtrack.platform.core.testing.FakeProjectRepository
import com.yellowtrack.platform.core.testing.FakeQuoteRepository
import com.yellowtrack.platform.core.testing.FakeServiceTemplateRepository
import com.yellowtrack.platform.core.testing.FakeSessionRepository
import com.yellowtrack.platform.core.testing.FakeStudioProfileRepository
import com.yellowtrack.platform.core.testing.RecordingDocumentSender
import com.yellowtrack.platform.core.testing.RecordingDocumentSink
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The figures the pricing floor is computed from.
 *
 * The floor itself is not editable and should not be: it exists because photographers price
 * by comparison with other photographers' guesses, and a floor somebody can type over is
 * another guess wearing the authority of a calculation. It would also be wrong in one
 * direction only — downwards, because the pressure is always to justify a price already
 * quoted.
 *
 * Its inputs are a different matter, and two of them could not be reached at all.
 * `annualOverrideOverride` and `desiredProfitMarginBasisPoints` have been on `CodbProfile`
 * since the calculator was written, are stored, and synchronise — and nothing in the
 * application could set either.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PricingBasisTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `overhead stated directly is what the floor is built from`() =
        runTest {
            val codb = FakeCodbRepository()
            val viewModel = viewModel(codb)

            viewModel.savePricingBasis("40000", "60", "30", "12000", "")

            assertEquals(
                requireNotNull(parseMoney("12000", CurrencyCode.USD)),
                codb.getProfile()?.annualOverheadOverride,
            )
        }

    /**
     * Blank is a decision rather than a missing answer: add up what I have logged.
     *
     * Stored as null rather than zero, because zero overhead is a *claim* — a studio with no
     * costs at all — and the calculator would believe it and produce a floor below cost.
     */
    @Test
    fun `leaving overhead blank goes back to the expenses that were logged`() =
        runTest {
            val codb = FakeCodbRepository()
            val viewModel = viewModel(codb)

            viewModel.savePricingBasis("40000", "60", "30", "", "")

            assertNull(codb.getProfile()?.annualOverheadOverride)
        }

    @Test
    fun `a profit margin is kept as basis points`() =
        runTest {
            val codb = FakeCodbRepository()
            val viewModel = viewModel(codb)

            viewModel.savePricingBasis("40000", "60", "30", "", "15")

            assertEquals(1_500, codb.getProfile()?.desiredProfitMarginBasisPoints)
        }

    @Test
    fun `no margin stated means none rather than a refusal to save`() =
        runTest {
            val codb = FakeCodbRepository()
            val viewModel = viewModel(codb)

            viewModel.savePricingBasis("40000", "60", "30", "", "")

            assertEquals(0, codb.getProfile()?.desiredProfitMarginBasisPoints)
        }

    /**
     * The form opens showing what is already saved, which is what makes it an adjustment
     * rather than a re-entry. Until now there was no way back to these figures at all — the
     * form only appeared while the floor was unconfigured, so the first answer a studio ever
     * typed was the one it kept.
     */
    @Test
    fun `the saved figures come back to the form`() =
        runTest {
            val codb = FakeCodbRepository()
            val viewModel = viewModel(codb)
            viewModel.savePricingBasis("40000", "60", "30", "12000", "15")

            val fields =
                viewModel.uiState
                    .map { (it.content as? UiState.Success)?.data?.pricingBasis }
                    .first { it != null && it.salary.isNotBlank() }!!
            assertEquals("12000.00", fields.annualOverhead)
            assertEquals("15", fields.profitMargin)
        }

    /** A correction replaces the figure rather than accumulating another profile. */
    @Test
    fun `adjusting twice leaves the second answer`() =
        runTest {
            val codb = FakeCodbRepository()
            val viewModel = viewModel(codb)

            viewModel.savePricingBasis("40000", "60", "30", "12000", "10")
            viewModel.savePricingBasis("45000", "50", "30", "", "20")

            val profile = requireNotNull(codb.getProfile())
            assertEquals(requireNotNull(parseMoney("45000", CurrencyCode.USD)), profile.targetAnnualSalary)
            assertEquals(50, profile.billableDaysPerYear)
            assertNull(profile.annualOverheadOverride, "cleared, not left at the old figure")
            assertEquals(2_000, profile.desiredProfitMarginBasisPoints)
        }

    // -- Fixtures --------------------------------------------------------------------------

    private fun viewModel(codb: FakeCodbRepository) =
        LedgerViewModel(
            invoiceRepository = FakeInvoiceRepository(),
            quoteRepository = FakeQuoteRepository(),
            contractRepository = FakeContractRepository(),
            expenseRepository = FakeExpenseRepository(),
            codbRepository = codb,
            serviceTemplateRepository = FakeServiceTemplateRepository(),
            projectRepository = FakeProjectRepository(),
            sessionRepository = FakeSessionRepository(),
            postProductionRepository = FakePostProductionRepository(),
            clientRepository = FakeClientRepository(),
            studioProfileRepository = FakeStudioProfileRepository(),
            documentSink = RecordingDocumentSink(),
            documentSender = RecordingDocumentSender(),
            studioContext = LocalStudioContext(),
            clock = TestAppClock(),
            timeZone = TimeZone.UTC,
        )
}
