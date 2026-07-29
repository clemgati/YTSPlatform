package com.yellowtrack.platform.feature.sessions

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.feature.sessions.presentation.component.SessionFormDialog
import com.yellowtrack.platform.feature.sessions.presentation.model.BookingOption
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the session form off-screen so it can be looked at.
 *
 * Colours read darker than the running application — the dialog's scrim composites over
 * the dialog as well as behind it. Layout and wording are faithful.
 */
class SessionFormRenderTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `renders the session form`() {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, "session-form.png")

        val scene =
            ImageComposeScene(width = 1_280, height = 1_800, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = YTTheme.colors.background,
                    ) {
                        SessionFormDialog(
                            bookings =
                                listOf(
                                    BookingOption(
                                        id = ProjectId.new(),
                                        label = "Johnson Wedding — Sarah & Michael Johnson",
                                    ),
                                ),
                            today = LocalDate(2026, 8, 15),
                            zone = TimeZone.of("Europe/London"),
                            onSave = {},
                            onDismiss = {},
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
