package com.yellowtrack.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.app.AppDestination
import com.yellowtrack.platform.app.components.CompactNavigationBar
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The phone's navigation, which nobody had looked at.
 *
 * The sidebar has had render tests since the day its landscape case was found clipped. This
 * bar had none, and it carried all seven destinations with every label cut to fit: on a 320dp
 * phone it read "Das…", "Clie…", "Ses…", "Led…", "Eve…", "Stu…", "Sett…". A studio hunting for
 * Events reported it missing, which is a fair reading of a tab labelled "Eve…".
 */
class CompactNavigationTest {
    /**
     * Every destination is reachable from a phone, one way or the other.
     *
     * The failure this guards against is not a wrong split; it is a destination added later
     * that belongs to neither list and can therefore be opened from nowhere. Derived rather
     * than listed for the same reason, so this is the check that the derivation holds.
     */
    @Test
    fun `every destination is either on the bar or behind more`() {
        val reachable = AppDestination.primary + AppDestination.overflow

        assertEquals(AppDestination.entries.toSet(), reachable.toSet())
        assertEquals(AppDestination.entries.size, reachable.size, "a destination is in both lists")
    }

    /** Events among them, since that is the one a studio could not find. */
    @Test
    fun `events is reachable`() {
        assertTrue(AppDestination.Events in AppDestination.primary + AppDestination.overflow)
    }

    /**
     * Few enough that the labels are words.
     *
     * Five is what fits at 320dp with nothing cut but the longest of them. This is the number
     * the whole change is about, so it is worth failing on rather than discovering in a
     * screenshot.
     */
    @Test
    fun `the bar carries few enough destinations to label them`() {
        assertTrue(
            AppDestination.primary.size <= 4,
            "${AppDestination.primary.size} destinations on the bar, plus More, will truncate",
        )
    }

    @Test
    fun `renders the bar on a narrow phone`() {
        render("bottom-bar-320.png", widthDp = 320)
    }

    @Test
    fun `renders the bar on an ordinary phone`() {
        render("bottom-bar-360.png", widthDp = 360)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        name: String,
        widthDp: Int,
    ) {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, name)

        val scene =
            ImageComposeScene(width = widthDp * 2, height = 260, density = Density(2f)) {
                YellowTrackTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize(), color = YTTheme.colors.background) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CompactNavigationBar(
                                currentDestination = AppDestination.Dashboard,
                                onDestinationSelected = {},
                            )
                        }
                    }
                }
            }

        val bytes =
            try {
                scene.render(500_000_000L).encodeToData()!!.bytes
            } finally {
                scene.close()
            }
        target.writeBytes(bytes)

        assertTrue(target.length() > 0, "nothing was written to ${target.absolutePath}")
    }
}
