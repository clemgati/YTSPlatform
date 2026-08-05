package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.expense.DistanceUnit
import com.yellowtrack.platform.core.model.expense.Expense
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.core.model.expense.ExpenseId
import com.yellowtrack.platform.core.model.expense.Mileage
import com.yellowtrack.platform.core.model.expense.MileageId
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
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
import com.yellowtrack.platform.feature.ledger.presentation.model.CostEdit
import com.yellowtrack.platform.feature.ledger.presentation.model.NewExpense
import com.yellowtrack.platform.feature.ledger.presentation.model.NewMileage
import com.yellowtrack.platform.feature.ledger.presentation.model.RecordedCost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A cost or a journey can be taken back off the year.
 *
 * Correcting one has been possible since costs became visible; removing one has not, and
 * the two are different admissions. A correction says the figure was wrong. A removal says
 * the cost was not this studio's at all — a personal card used by accident, or the same
 * receipt entered twice on two devices.
 *
 * Correcting a duplicate to zero is the workaround, and it is a bad one: it leaves a
 * phantom row in the itemised year that the studio meets again at tax time and has to work
 * out from scratch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CostRemovalTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a cost entered twice can be taken off`() =
        runTest {
            val expenses = FakeExpenseRepository(listOf(expense("e1"), expense("e2")))
            val viewModel = viewModel(expenses)

            viewModel.removeCost(cost("e2"))

            assertEquals(
                listOf(ExpenseId("e1")),
                expenses.observeExpenses().first().map { it.id },
                "the duplicate goes; the real one stays",
            )
        }

    @Test
    fun `removing a cost leaves the journeys alone`() =
        runTest {
            val expenses = FakeExpenseRepository(listOf(expense("e1")), listOf(journey("m1")))
            val viewModel = viewModel(expenses)

            viewModel.removeCost(cost("e1"))

            assertEquals(
                listOf(MileageId("m1")),
                expenses.observeMileage().first().map { it.id },
                "costs and journeys share a repository and are not the same records; an " +
                    "identifier from one must never reach the other",
            )
        }

    @Test
    fun `a journey can be taken off`() =
        runTest {
            val expenses = FakeExpenseRepository(listOf(expense("e1")), listOf(journey("m1")))
            val viewModel = viewModel(expenses)

            viewModel.removeCost(journeyRow("m1"))

            assertTrue(expenses.observeMileage().first().isEmpty())
            assertEquals(
                listOf(ExpenseId("e1")),
                expenses.observeExpenses().first().map { it.id },
                "and the cost is untouched, for the same reason",
            )
        }

    // -- Fixtures ----------------------------------------------------------------------------

    private fun viewModel(expenses: FakeExpenseRepository) =
        LedgerViewModel(
            invoiceRepository = FakeInvoiceRepository(),
            quoteRepository = FakeQuoteRepository(),
            contractRepository = FakeContractRepository(),
            expenseRepository = expenses,
            codbRepository = FakeCodbRepository(expenses = expenses),
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

    /** A cost row as the Ledger builds it: the form it reopens is what says which it is. */
    private fun cost(id: String) =
        RecordedCost(
            id = id,
            date = "1 Aug",
            description = "Insurance renewal",
            amount = "£420.00",
            allocation = "Overhead",
            editable =
                CostEdit.OfExpense(
                    NewExpense(
                        description = "Insurance renewal",
                        amount = "420.00",
                        category = ExpenseCategory.Other,
                        incurredOn = DAY.toString(),
                        projectId = null,
                        vendor = null,
                        isTaxDeductible = true,
                    ),
                ),
        )

    private fun journeyRow(id: String) =
        RecordedCost(
            id = id,
            date = "1 Aug",
            description = "42 miles",
            amount = "£18.90",
            allocation = "Overhead",
            editable =
                CostEdit.OfJourney(
                    NewMileage(
                        travelledOn = DAY.toString(),
                        distance = "42",
                        unit = DistanceUnit.Miles,
                        ratePerUnit = "0.45",
                        projectId = null,
                        purpose = null,
                        fromLocation = null,
                        toLocation = null,
                    ),
                ),
        )

    private fun expense(id: String) =
        Expense(
            id = ExpenseId(id),
            studioId = STUDIO,
            category = ExpenseCategory.Other,
            description = "Insurance renewal",
            amount = Money(minorUnits = 42_000, currency = CurrencyCode.GBP),
            incurredOn = DAY,
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private fun journey(id: String) =
        Mileage(
            id = MileageId(id),
            studioId = STUDIO,
            travelledOn = DAY,
            distance = 42.0,
            unit = DistanceUnit.Miles,
            ratePerUnit = Money(minorUnits = 45, currency = CurrencyCode.GBP),
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private companion object {
        val STUDIO = StudioId("studio-1")
        val DAY: LocalDate = LocalDate.parse("2026-08-01")
    }
}
