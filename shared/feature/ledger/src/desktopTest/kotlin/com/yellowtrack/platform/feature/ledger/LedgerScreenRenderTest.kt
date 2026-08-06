package com.yellowtrack.platform.feature.ledger

import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.expense.DistanceUnit
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.InvoiceKind
import com.yellowtrack.platform.core.model.invoice.PaymentState
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.ledger.presentation.LedgerContent
import com.yellowtrack.platform.feature.ledger.presentation.LedgerScreen
import com.yellowtrack.platform.feature.ledger.presentation.LedgerUiState
import com.yellowtrack.platform.feature.ledger.presentation.PricingBasisFields
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractStage
import com.yellowtrack.platform.feature.ledger.presentation.model.CostEdit
import com.yellowtrack.platform.feature.ledger.presentation.model.DraftInvoiceItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ExpenseSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.MoneyOwedSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.NewContract
import com.yellowtrack.platform.feature.ledger.presentation.model.NewExpense
import com.yellowtrack.platform.feature.ledger.presentation.model.NewInvoice
import com.yellowtrack.platform.feature.ledger.presentation.model.NewLineItem
import com.yellowtrack.platform.feature.ledger.presentation.model.NewMileage
import com.yellowtrack.platform.feature.ledger.presentation.model.NewQuote
import com.yellowtrack.platform.feature.ledger.presentation.model.NewServiceTemplate
import com.yellowtrack.platform.feature.ledger.presentation.model.OutstandingInvoiceItem
import com.yellowtrack.platform.feature.ledger.presentation.model.PackagePricing
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import com.yellowtrack.platform.feature.ledger.presentation.model.ProposalsSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.QuoteItem
import com.yellowtrack.platform.feature.ledger.presentation.model.RecordedCost
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the Ledger off-screen and writes a PNG.
 *
 * Compose Desktop can rasterise a composition without opening a window, which is the only
 * way to look at a screen on a machine where screen recording is unavailable. The
 * assertion is deliberately weak — this exists so a person can open the image, not so CI
 * can decide the layout is correct.
 *
 * Set `-Dyellowtrack.render.dir` to choose where the images land.
 */
class LedgerScreenRenderTest {
    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        name: String,
        width: Int,
        height: Int,
        bookings: Boolean = true,
    ) {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, name)

        val scene =
            ImageComposeScene(
                width = width,
                height = height,
                density = Density(2f),
            ) {
                YellowTrackTheme {
                    // The screen paints no background of its own — the app shell does —
                    // so without this the theme's light-on-dark text renders onto white
                    // and the heading disappears.
                    Surface(color = YTTheme.colors.background) {
                        LedgerScreen(
                            uiState = LedgerUiState(content = UiState.Success(sampleContent(bookings))),
                            onRetry = {},
                            onSavePricingBasis = { _, _, _ -> },
                            onSaveExpense = { _, _ -> },
                            onSaveMileage = { _, _ -> },
                            onRemoveCost = {},
                            onSavePackage = { _, _ -> },
                            onRemovePackage = {},
                            onRemovePayment = {},
                            onRecordPayment = {},
                            onSaveQuote = { _, _ -> },
                            onSaveInvoice = { _, _ -> },
                            onSaveContract = { _, _ -> },
                            onAcceptQuote = {},
                            onDeclineQuote = {},
                            onSendContract = {},
                            onSignContract = {},
                            onSendInvoice = {},
                            onVoidInvoice = {},
                            onDeleteInvoice = {},
                            onExportInvoice = {},
                            onExportQuote = {},
                            onEmailInvoice = { _, _ -> },
                            onEmailQuote = { _, _ -> },
                            documentMessage = null,
                        )
                    }
                }
            }

        try {
            val bytes = requireNotNull(scene.render().encodeToData()) { "Skia produced no image data" }.bytes
            target.writeBytes(bytes)
        } finally {
            scene.close()
        }

        assertTrue(target.length() > 0, "expected a non-empty image at ${target.absolutePath}")
        println("Rendered ${target.absolutePath}")
    }

    /** The Ledger as a desktop shows it. */
    @Test
    fun `renders the ledger to a png`() {
        render("ledger.png", width = WIDTH, height = HEIGHT)
    }

    /**
     * And as a phone does.
     *
     * The Ledger has the most crowded rows in the application — an outstanding invoice
     * carries three actions, a quote four. At 640dp they all fit, which says nothing about
     * a phone: the Studio screen fitted too, right up until it was photographed.
     */
    @Test
    fun `renders the ledger on a phone`() {
        render("ledger-phone.png", width = PHONE, height = 9_000)
    }

    /**
     * A studio with no bookings yet, on a phone.
     *
     * The state the screenshots that found this were taken in. All three documents are
     * priced against a booking, so with none the forms open and cannot be completed — which
     * reads as three buttons that do not work unless the screen says otherwise.
     */
    @Test
    fun `renders the ledger for a studio with no bookings`() {
        render("ledger-no-bookings-phone.png", width = PHONE, height = 9_000, bookings = false)
    }

    private fun sampleContent(bookings: Boolean = true) =
        LedgerContent(
            moneyOwed =
                MoneyOwedSummary(
                    totalOutstanding = "$6,750.00",
                    overdueAmount = "$2,250.00",
                    overdueCount = 1,
                    invoices =
                        listOf(
                            OutstandingInvoiceItem(
                                id = InvoiceId.new(),
                                number = "INV-004",
                                clientName = "Sarah & Michael Johnson",
                                projectName = "Johnson Wedding",
                                balanceDue = "$2,250.00",
                                balanceDuePlain = "2250.00",
                                state = PaymentState.Overdue,
                                overdueDays = 12,
                                dueLabel = "16 Jul 2026",
                                canVoid = true,
                                emailedLabel = "Emailed to sarah@johnson.example on 5 Aug 2026",
                            ),
                            OutstandingInvoiceItem(
                                id = InvoiceId.new(),
                                number = "INV-006",
                                clientName = "Harbourline Coffee",
                                projectName = "Autumn Brand Shoot",
                                balanceDue = "$4,500.00",
                                balanceDuePlain = "4500.00",
                                state = PaymentState.PartiallyPaid,
                                overdueDays = null,
                                dueLabel = "11 Aug 2026",
                                // Part paid, so voiding is not offered on this row.
                                canVoid = false,
                                emailedLabel = null,
                            ),
                        ),
                    drafts =
                        listOf(
                            DraftInvoiceItem(
                                id = InvoiceId.new(),
                                number = "INV-007",
                                clientName = "Ada Okafor",
                                projectName = "Okafor Portraits",
                                total = "$300.00",
                                raisedLabel = "raised 5 days ago",
                                editable = sampleInvoiceForm,
                            ),
                        ),
                ),
            proposals =
                ProposalsSummary(
                    awaitingDecision =
                        listOf(
                            QuoteItem(
                                id = QuoteId.new(),
                                number = "QUO-009",
                                clientName = "Priya & Tom Sandhu",
                                projectName = "Sandhu Wedding",
                                total = "$5,400.00",
                                status = QuoteStatus.Expired,
                                waitingLabel = "sent 6 weeks ago",
                                emailedLabel = null,
                                validUntilLabel = "30 Jun 2026",
                                editable = sampleQuoteForm,
                            ),
                            QuoteItem(
                                id = QuoteId.new(),
                                number = "QUO-011",
                                clientName = "Northgate Architects",
                                projectName = "Office Portraits",
                                total = "$1,850.00",
                                status = QuoteStatus.Sent,
                                waitingLabel = "sent 4 days ago",
                                emailedLabel = null,
                                validUntilLabel = "24 Aug 2026",
                                editable = sampleQuoteForm,
                            ),
                        ),
                    // One at each stage, so the image shows every action the row can offer.
                    datesNotHeld =
                        listOf(
                            ContractItem(
                                id = ContractId.new(),
                                title = "Okafor Portrait Agreement",
                                clientName = "Ada Okafor",
                                retainer = "$300.00",
                                stage = ContractStage.NotSent,
                                waitingLabel = "drawn up 3 days ago",
                                editable = sampleContractForm,
                            ),
                            ContractItem(
                                id = ContractId.new(),
                                title = "Sandhu Wedding Agreement",
                                clientName = "Priya & Tom Sandhu",
                                retainer = "$2,700.00",
                                stage = ContractStage.AwaitingSignature,
                                waitingLabel = "sent 9 days ago",
                                editable = sampleContractForm,
                            ),
                            ContractItem(
                                id = ContractId.new(),
                                title = "Harbourline Brand Agreement",
                                clientName = "Harbourline Coffee",
                                retainer = "$1,500.00",
                                stage = ContractStage.AwaitingRetainer,
                                waitingLabel = "signed 2 days ago",
                                editable = sampleContractForm,
                            ),
                        ),
                    quotedValue = "$7,250.00",
                    expiredCount = 1,
                    nextQuoteNumber = "QUO-012",
                    nextInvoiceNumber = "INV-007",
                ),
            pricing = null,
            expenses =
                ExpenseSummary(
                    year = 2026,
                    overheadTotal = "$14,320.00",
                    jobCostTotal = "$3,180.00",
                    mileageDeduction = "$642.50",
                    recorded = 37,
                    // Itemised, because totals alone meant a studio could record a cost and
                    // never see it again. Both kinds are here: the list does not care which
                    // table a row came from, and neither does the person reading it.
                    items =
                        listOf(
                            RecordedCost(
                                id = "e1",
                                date = "Jul 26",
                                description = "Second shooter — Okafor wedding",
                                amount = "$450.00",
                                allocation = "Okafor — Wedding",
                                editable = CostEdit.OfExpense(sampleExpenseForm),
                            ),
                            RecordedCost(
                                id = "m1",
                                date = "Jul 24",
                                description = "Venue recce",
                                amount = "$18.90",
                                allocation = "Overhead",
                                editable = CostEdit.OfJourney(sampleJourneyForm),
                            ),
                            RecordedCost(
                                id = "e2",
                                date = "Jul 02",
                                description = "Insurance renewal",
                                amount = "$1,240.00",
                                allocation = "Overhead",
                                editable = CostEdit.OfExpense(sampleExpenseForm),
                            ),
                        ),
                ),
            // No pricing basis is set in this fixture, which is the case worth looking at:
            // the packages have to be reachable before a floor exists, or a new studio can
            // never touch the four it was given.
            packages =
                listOf(
                    PackagePricing(
                        id = "t1",
                        name = "Full-day wedding",
                        serviceLine = "Wedding",
                        price = "$2,400.00",
                        minimumPrice = null,
                        difference = null,
                        estimatedDays = "3.0 days",
                        isBelowCost = false,
                        hasPrice = true,
                        editable = samplePackageForm,
                    ),
                    PackagePricing(
                        id = "t2",
                        name = "Headshot session",
                        serviceLine = "Headshot",
                        price = "—",
                        minimumPrice = null,
                        difference = null,
                        estimatedDays = "0.5 days",
                        isBelowCost = false,
                        hasPrice = false,
                        editable = samplePackageForm,
                    ),
                ),
            projects =
                if (bookings) {
                    listOf(ProjectOption(id = ProjectId("project-1"), label = "Okafor — Wedding"))
                } else {
                    emptyList()
                },
            today = LocalDate(2026, 7, 28),
            currency = CurrencyCode.USD,
            pricingBasis = PricingBasisFields(currency = CurrencyCode.USD),
        )

    private companion object {
        val sampleInvoiceForm =
            NewInvoice(
                number = "2026-014",
                projectId = ProjectId("project-1"),
                kind = InvoiceKind.Balance,
                lines = listOf(NewLineItem(description = "Coverage", unitPrice = "1200.00")),
                dueOn = "2026-09-01",
                sendNow = false,
            )

        val sampleQuoteForm =
            NewQuote(
                number = "Q-2026-014",
                projectId = ProjectId("project-1"),
                lines = listOf(NewLineItem(description = "Coverage", unitPrice = "1200.00")),
                validUntil = "",
                terms = null,
            )

        val sampleContractForm =
            NewContract(
                projectId = ProjectId("project-1"),
                title = "Wedding coverage",
                retainerAmount = "",
                isRetainerRefundable = false,
                turnaroundDays = "",
                revisionRounds = "",
                cancellationTerms = null,
                rescheduleTerms = null,
                weatherClause = null,
                license = null,
                sendNow = false,
            )

        val samplePackageForm =
            NewServiceTemplate(
                name = "Full-day wedding",
                serviceLine = ServiceLine.Wedding,
                sessionDurationMinutes = "600",
                sessionCount = "1",
                basePrice = "2400.00",
                deliverableCount = "",
                turnaroundDays = "",
                revisionRounds = "",
                notes = "",
            )

        val sampleExpenseForm =
            NewExpense(
                description = "Second shooter — Okafor wedding",
                amount = "450.00",
                category = ExpenseCategory.Other,
                incurredOn = "2026-07-26",
                projectId = null,
                vendor = null,
                isTaxDeductible = true,
            )

        val sampleJourneyForm =
            NewMileage(
                travelledOn = "2026-07-24",
                distance = "42",
                unit = DistanceUnit.Miles,
                ratePerUnit = "0.45",
                projectId = null,
                purpose = "Venue recce",
                fromLocation = null,
                toLocation = null,
            )

        const val WIDTH = 1_280

        /** A 390pt phone at 2x. */
        const val PHONE = 780

        /**
         * Tall enough that the scrolling column is captured whole rather than clipped.
         *
         * The itemised costs sit below the pricing floor, and at 3,400 the render stopped
         * short of them — which is a render test that does not cover the section it is
         * supposed to.
         */
        const val HEIGHT = 5_400
    }
}
