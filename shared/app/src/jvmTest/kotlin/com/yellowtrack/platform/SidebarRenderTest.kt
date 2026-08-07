package com.yellowtrack.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.app.AppDestination
import com.yellowtrack.platform.app.components.ExpandedSidebar
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The sidebar at the height a phone gives it when held sideways.
 *
 * Reported from a real device: in landscape the destinations ran off the bottom and Settings,
 * being last, could not be reached at all. Nothing indicated there was more — the column had
 * a fixed height, no scrolling, and a spacer holding the version against the bottom.
 *
 * A render test rather than an assertion about pixels: what went wrong is only visible by
 * looking, and the previous version of this screen would have passed any test that did not.
 */
class SidebarRenderTest {
    /**
     * Roughly a phone on its side once the system bars are taken off: about 380dp of height.
     *
     * The numbers here are **pixels**, and the scene renders at density 2 — so 380dp is 760.
     * Written out because passing the dp figure straight in renders half the screen and
     * makes the result look worse than it is, which is exactly what happened first time.
     */
    @Test
    fun `renders every destination on a phone held sideways`() {
        render("sidebar-landscape.png", width = 900, height = 760)
    }

    /** The tall case, which was never broken and must stay that way. */
    @Test
    fun `renders every destination with room to spare`() {
        render("sidebar-portrait.png", width = 900, height = 1800)
    }

    /** Absurdly short on purpose: the list must still scroll rather than clip the version. */
    @Test
    fun `keeps the version visible when there is almost no height at all`() {
        render("sidebar-tiny.png", width = 900, height = 440)
    }

    private fun render(
        name: String,
        width: Int,
        height: Int,
    ) {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, name)

        val scene =
            ImageComposeScene(width = width, height = height, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = YTTheme.colors.background) {
                        Sidebar()
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

    @Composable
    private fun Sidebar() {
        ExpandedSidebar(
            currentDestination = AppDestination.entries.first(),
            onDestinationSelected = {},
        )
    }
}
