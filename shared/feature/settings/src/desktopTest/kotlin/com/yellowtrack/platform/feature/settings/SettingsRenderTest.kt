package com.yellowtrack.platform.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.settings.presentation.SettingsContent
import com.yellowtrack.platform.feature.settings.presentation.SettingsScreen
import com.yellowtrack.platform.feature.settings.presentation.SettingsUiState
import com.yellowtrack.platform.feature.settings.presentation.StudioProfileFields
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the Settings screen, which has been a placeholder since 0.1.0. */
class SettingsRenderTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `renders the studio details to a png`() {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, "settings.png")

        val scene =
            ImageComposeScene(width = 1_280, height = 2_400, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = YTTheme.colors.background,
                    ) {
                        SettingsScreen(
                            uiState = UiState.Success(sampleContent()).let(::SettingsUiState),
                            onRetry = {},
                            onSave = {},
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

    // Deliberately half-filled: the gaps line is the part worth looking at.
    private fun sampleContent() =
        SettingsContent(
            profile =
                StudioProfileFields(
                    name = "Yellow Track Studios",
                    address = "12 Harbour Road\nFalmouth\nTR11 3AA",
                    email = "hello@yellowtrack.example",
                    phone = "07700 900000",
                    website = "yellowtrack.example",
                ),
            canIssueDocuments = true,
            gaps = listOf("no tax registration number", "no payment instructions"),
            savedNote = "Saved. Your documents will carry these details.",
        )
}
