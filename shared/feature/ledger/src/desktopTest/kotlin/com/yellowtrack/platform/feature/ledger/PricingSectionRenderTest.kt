package com.yellowtrack.platform.feature.ledger

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.ledger.presentation.PricingBasisFields
import com.yellowtrack.platform.feature.ledger.presentation.component.PricingSection
import com.yellowtrack.platform.feature.ledger.presentation.model.PricingSummary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The pricing floor, and the figures behind it.
 *
 * Rendered because the two new inputs and the way back to them are the whole change, and
 * neither is visible from a test about the view model. The floor stays read-only in every
 * one of these — that is the property being looked at, not just the new fields.
 */
class PricingSectionRenderTest {
    /** What a configured studio sees: the floor, the working, and a way back to the figures. */
    @Test
    fun `renders the floor with the figures folded away`() {
        render("pricing-configured.png", height = 1_400) {
            PricingSection(pricing = summary(), basis = basis(), onSaveBasis = { _, _, _, _, _ -> })
        }
    }

    /**
     * The state a new studio starts in. Overhead and profit are on this form too — a studio
     * with no expenses logged yet is exactly the one whose floor comes out too low without
     * stating overhead directly.
     */
    @Test
    fun `renders the setup form before a basis exists`() {
        render("pricing-unconfigured.png", height = 2_000) {
            PricingSection(pricing = null, basis = PricingBasisFields(), onSaveBasis = { _, _, _, _, _ -> })
        }
    }

    /** Narrow, because the help text under each field is where a phone runs out of room. */
    @Test
    fun `renders the setup form on a phone`() {
        render("pricing-unconfigured-phone.png", width = 760, height = 2_400) {
            PricingSection(pricing = null, basis = PricingBasisFields(), onSaveBasis = { _, _, _, _, _ -> })
        }
    }

    private fun render(
        name: String,
        height: Int,
        width: Int = 1_400,
        content: @Composable () -> Unit,
    ) {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, name)

        val scene =
            ImageComposeScene(width = width, height = height, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = YTTheme.colors.background) {
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

    private fun summary() =
        PricingSummary(
            postProductionFactor = 1.5,
            isFactorMeasured = false,
            costPerBillableDay = "$1,428.58",
            annualOverhead = "$12,000.00",
            targetSalary = "$40,000.00",
            taxAllowance = "$17,142.86",
            profitAllowance = "$16,571.43",
            totalAnnualRequirement = "$85,714.29",
            billableDaysPerYear = 60,
        )

    private fun basis() =
        PricingBasisFields(
            salary = "40000.00",
            billableDays = "60",
            taxRate = "30",
            annualOverhead = "12000.00",
            profitMargin = "15",
            currency = CurrencyCode.USD,
        )
}
