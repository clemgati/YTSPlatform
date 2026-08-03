package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
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
import com.yellowtrack.platform.core.testing.RecordingDocumentSink
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.feature.ledger.presentation.LedgerViewModel
import com.yellowtrack.platform.feature.ledger.presentation.model.NewServiceTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertTrue

/**
 * A studio can have its own packages.
 *
 * Until now the four seeded ones were the only packages that could ever exist: `saveTemplate`
 * was never called from any screen. So a studio could not add the thing it actually sells,
 * and could not correct a default's price — while the pricing floor, whose entire job is to
 * say which packages fall short, went on measuring packages nobody had agreed to.
 *
 * That makes this the one remaining sweep finding that distorted a number the application
 * presents as advice, rather than merely withholding an action.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceTemplateEditingTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a studio can add the package it actually sells`() =
        runTest {
            val templates = FakeServiceTemplateRepository()
            val viewModel = viewModel(templates)

            viewModel.saveServiceTemplate(form(name = "Elopement, half day"))

            assertEquals(
                listOf("Elopement, half day"),
                templates.observeTemplates().first().map { it.name },
            )
        }

    @Test
    fun `correcting a default's price keeps the same package`() =
        runTest {
            val templates = FakeServiceTemplateRepository(listOf(seeded()))
            val viewModel = viewModel(templates)

            viewModel.saveServiceTemplate(form(basePrice = "1800.00"), existingId = ID.value)

            val stored = templates.observeTemplates().first().single()
            assertEquals(ID, stored.id, "an edit corrects the package rather than adding a rival to it")
            assertEquals(180_000L, stored.basePrice?.minorUnits)
        }

    @Test
    fun `a blank price means undecided rather than free`() =
        runTest {
            val templates = FakeServiceTemplateRepository(listOf(seeded()))
            val viewModel = viewModel(templates)

            viewModel.saveServiceTemplate(form(basePrice = "  "), existingId = ID.value)

            assertNull(
                templates
                    .observeTemplates()
                    .first()
                    .single()
                    .basePrice,
                "a package with no figure agreed still has a floor, and seeing the floor is " +
                    "how the figure gets decided",
            )
        }

    @Test
    fun `a price that will not parse is not saved as no price`() =
        runTest {
            val templates = FakeServiceTemplateRepository(listOf(seeded()))
            val viewModel = viewModel(templates)

            viewModel.saveServiceTemplate(form(basePrice = "twelve hundred"), existingId = ID.value)

            assertEquals(
                120_000L,
                templates
                    .observeTemplates()
                    .first()
                    .single()
                    .basePrice
                    ?.minorUnits,
                "silently reading an unparseable figure as \"undecided\" would discard what " +
                    "somebody meant and leave the floor comparing against nothing",
            )
        }

    @Test
    fun `a package with no shooting time is not saved`() =
        runTest {
            val templates = FakeServiceTemplateRepository(listOf(seeded()))
            val viewModel = viewModel(templates)

            viewModel.saveServiceTemplate(form(sessionDurationMinutes = "0"), existingId = ID.value)

            assertEquals(
                120,
                templates
                    .observeTemplates()
                    .first()
                    .single()
                    .defaultSessionDurationMinutes,
                "duration is what the floor multiplies; a package of zero days clears any " +
                    "floor and would report itself comfortably profitable",
            )
        }

    @Test
    fun `a package a studio does not sell can be removed`() =
        runTest {
            val templates = FakeServiceTemplateRepository(listOf(seeded()))
            val viewModel = viewModel(templates)

            viewModel.removeServiceTemplate(ID.value)

            assertTrue(
                templates.observeTemplates().first().isEmpty(),
                "a seeded default a studio does not offer should not be measured against its " +
                    "floor for ever",
            )
        }

    // -- Fixtures ----------------------------------------------------------------------------

    private fun viewModel(templates: FakeServiceTemplateRepository): LedgerViewModel {
        val expenses = FakeExpenseRepository()

        return LedgerViewModel(
            invoiceRepository = FakeInvoiceRepository(),
            quoteRepository = FakeQuoteRepository(),
            contractRepository = FakeContractRepository(),
            expenseRepository = expenses,
            codbRepository = FakeCodbRepository(expenses = expenses),
            serviceTemplateRepository = templates,
            projectRepository = FakeProjectRepository(),
            sessionRepository = FakeSessionRepository(),
            postProductionRepository = FakePostProductionRepository(),
            clientRepository = FakeClientRepository(),
            studioProfileRepository = FakeStudioProfileRepository(),
            documentSink = RecordingDocumentSink(),
            studioContext = LocalStudioContext(),
            clock = TestAppClock(),
            timeZone = TimeZone.UTC,
        )
    }

    private fun form(
        name: String = "Full-day wedding",
        basePrice: String = "1200.00",
        sessionDurationMinutes: String = "480",
    ) = NewServiceTemplate(
        name = name,
        serviceLine = ServiceLine.Wedding,
        sessionDurationMinutes = sessionDurationMinutes,
        sessionCount = "1",
        basePrice = basePrice,
        deliverableCount = "",
        turnaroundDays = "",
        revisionRounds = "",
        notes = "",
    )

    private fun seeded() =
        ServiceTemplate(
            id = ID,
            studioId = STUDIO,
            name = "Full-day wedding",
            serviceLine = ServiceLine.Wedding,
            defaultSessionDurationMinutes = 120,
            basePrice = Money(minorUnits = 120_000, currency = CurrencyCode.USD),
            audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
        )

    private companion object {
        val STUDIO = StudioId("studio-1")
        val ID = ServiceTemplateId("template-1")
    }
}
