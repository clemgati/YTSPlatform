package com.yellowtrack.platform.feature.ledger

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.model.contract.ContractId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.feature.ledger.presentation.component.ContractFormDialog
import com.yellowtrack.platform.feature.ledger.presentation.component.ContractSignatureDialog
import com.yellowtrack.platform.feature.ledger.presentation.component.QuoteFormDialog
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractStage
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the contract dialogs off-screen so they can be looked at.
 *
 * Every form in this application had until now been reasoned about and tested but never
 * seen. The contract form is the longest of them, and the one most able to go wrong
 * silently — a dialog that overflows its own bounds still passes every unit test. Looking
 * at this one is what found the form height cap that was hiding two thirds of its fields.
 *
 * Read the colours in these images with care: off-screen, the dialog's scrim composites
 * over the dialog as well as behind it, so everything comes out darker and flatter than it
 * appears in the running application. Layout, wording, and what is above the fold are
 * faithful; contrast is not.
 *
 * Set `-Dyellowtrack.render.dir` to choose where the images land.
 */
class ContractFormRenderTest {
    private val today = LocalDate(2026, 7, 28)

    private fun outputDir(): File =
        File(System.getProperty("yellowtrack.render.dir") ?: "build/render").also { it.mkdirs() }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val target = File(outputDir(), name)

        val scene =
            ImageComposeScene(
                width = WIDTH,
                height = HEIGHT,
                density = Density(2f),
            ) {
                YellowTrackTheme {
                    // Sized explicitly: a dialog occupies no layout space, so a Surface
                    // wrapping only the dialog collapses to nothing and paints no
                    // background at all. The image would then be the dialog's scrim over
                    // white, which is not what anyone will ever see.
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = YTTheme.colors.background,
                    ) {
                        content()
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

    @Test
    fun `renders the contract form`() {
        render("contract-form.png") {
            ContractFormDialog(
                today = today,
                currency = CurrencyCode.USD,
                projects =
                    listOf(
                        ProjectOption(id = ProjectId.new(), label = "Johnson Wedding — Sarah & Michael Johnson"),
                    ),
                onSave = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun `renders the quote form with several lines`() {
        render("quote-form.png") {
            QuoteFormDialog(
                suggestedNumber = "QUO-012",
                today = today,
                currency = CurrencyCode.USD,
                projects =
                    listOf(
                        ProjectOption(id = ProjectId.new(), label = "Johnson Wedding — Sarah & Michael Johnson"),
                    ),
                onSave = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun `renders the signature form`() {
        render("contract-signature.png") {
            ContractSignatureDialog(
                contract =
                    ContractItem(
                        id = ContractId.new(),
                        title = "Sandhu Wedding Agreement",
                        clientName = "Priya & Tom Sandhu",
                        retainer = "$2,700.00",
                        stage = ContractStage.AwaitingSignature,
                        waitingLabel = "sent 9 days ago",
                    ),
                today = today,
                onSave = {},
                onDismiss = {},
            )
        }
    }

    private companion object {
        const val WIDTH = 1_280
        const val HEIGHT = 1_800
    }
}
