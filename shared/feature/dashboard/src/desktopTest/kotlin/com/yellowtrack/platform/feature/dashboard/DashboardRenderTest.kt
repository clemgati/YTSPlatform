package com.yellowtrack.platform.feature.dashboard

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardScreen
import com.yellowtrack.platform.feature.dashboard.presentation.DashboardUiState
import com.yellowtrack.platform.feature.dashboard.presentation.preview.DashboardPreviewData
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the Dashboard with work that synchronisation discarded.
 *
 * Exists to be looked at. The banner's whole job is to be noticed by somebody who was not
 * looking for it, and that is not a property an assertion can check — so this writes the
 * png and a human decides whether it does its job.
 */
class DashboardRenderTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `renders the dashboard with overwritten work to a png`() {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, "dashboard-conflicts.png")

        val scene =
            ImageComposeScene(width = 1_280, height = 1_600, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = YTTheme.colors.background,
                    ) {
                        DashboardScreen(
                            uiState =
                                DashboardUiState(
                                    summary =
                                        UiState.Success(
                                            DashboardPreviewData.summary.copy(
                                                unresolvedConflicts = 2,
                                                // The fixture leaves this blank, which
                                                // renders the date badge as an empty pill
                                                // and makes the image lie about the header.
                                                todayLabel = "Friday, July 31",
                                            ),
                                        ),
                                ),
                            onRetry = {},
                            onMarkEnquiryReplied = {},
                            onAddEnquiry = {},
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
}
