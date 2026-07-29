package com.yellowtrack.platform.feature.ledger

import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.invoice.InvoiceId
import com.yellowtrack.platform.core.model.invoice.PaymentState
import com.yellowtrack.platform.core.model.quote.QuoteId
import com.yellowtrack.platform.core.model.quote.QuoteStatus
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.ledger.presentation.LedgerContent
import com.yellowtrack.platform.feature.ledger.presentation.LedgerScreen
import com.yellowtrack.platform.feature.ledger.presentation.LedgerUiState
import com.yellowtrack.platform.feature.ledger.presentation.PricingBasisFields
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractStage
import com.yellowtrack.platform.feature.ledger.presentation.model.ExpenseSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.MoneyOwedSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.OutstandingInvoiceItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ProposalsSummary
import com.yellowtrack.platform.feature.ledger.presentation.model.QuoteItem
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
    @Test
    fun `renders the ledger to a png`() {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, "ledger.png")

        val scene =
            ImageComposeScene(
                width = WIDTH,
                height = HEIGHT,
                density = Density(2f),
            ) {
                YellowTrackTheme {
                    // The screen paints no background of its own — the app shell does —
                    // so without this the theme's light-on-dark text renders onto white
                    // and the heading disappears.
                    Surface(color = YTTheme.colors.background) {
                        LedgerScreen(
                            uiState = LedgerUiState(content = UiState.Success(sampleContent())),
                            onRetry = {},
                            onSavePricingBasis = { _, _, _ -> },
                            onAddExpense = {},
                            onRecordPayment = {},
                            onAddQuote = {},
                            onAddInvoice = {},
                            onAddContract = {},
                            onAcceptQuote = {},
                            onDeclineQuote = {},
                            onSendContract = {},
                            onSignContract = {},
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

    private fun sampleContent() =
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
                            ),
                            OutstandingInvoiceItem(
                                id = InvoiceId.new(),
                                number = "INV-006",
                                clientName = "Harbourline Coffee",
                                projectName = "Autumn Brand Shoot",
                                balanceDue = "$4,500.00",
                                balanceDuePlain = "4500.00",
                                state = PaymentState.AwaitingPayment,
                                overdueDays = null,
                                dueLabel = "11 Aug 2026",
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
                                validUntilLabel = "30 Jun 2026",
                            ),
                            QuoteItem(
                                id = QuoteId.new(),
                                number = "QUO-011",
                                clientName = "Northgate Architects",
                                projectName = "Office Portraits",
                                total = "$1,850.00",
                                status = QuoteStatus.Sent,
                                waitingLabel = "sent 4 days ago",
                                validUntilLabel = "24 Aug 2026",
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
                            ),
                            ContractItem(
                                id = ContractId.new(),
                                title = "Sandhu Wedding Agreement",
                                clientName = "Priya & Tom Sandhu",
                                retainer = "$2,700.00",
                                stage = ContractStage.AwaitingSignature,
                                waitingLabel = "sent 9 days ago",
                            ),
                            ContractItem(
                                id = ContractId.new(),
                                title = "Harbourline Brand Agreement",
                                clientName = "Harbourline Coffee",
                                retainer = "$1,500.00",
                                stage = ContractStage.AwaitingRetainer,
                                waitingLabel = "signed 2 days ago",
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
                ),
            projects = emptyList(),
            today = LocalDate(2026, 7, 28),
            currency = CurrencyCode.USD,
            pricingBasis = PricingBasisFields(currency = CurrencyCode.USD),
        )

    private companion object {
        const val WIDTH = 1_280

        /** Tall enough that the scrolling column is captured whole rather than clipped. */
        const val HEIGHT = 3_400
    }
}
