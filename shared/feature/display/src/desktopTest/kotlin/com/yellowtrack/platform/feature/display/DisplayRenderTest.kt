package com.yellowtrack.platform.feature.display

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.model.event.QrMatrix
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.display.presentation.DisplayContent
import com.yellowtrack.platform.feature.display.presentation.DisplayScreen
import com.yellowtrack.platform.feature.display.presentation.DisplayUiState
import com.yellowtrack.platform.feature.display.presentation.DisplayableEvent
import com.yellowtrack.platform.feature.display.presentation.Showing
import com.yellowtrack.platform.feature.display.presentation.Unlock
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders the display so somebody can look at it, and then reads the code back off the screen.
 *
 * The looking matters more here than anywhere else in the project. This screen has no user —
 * nobody is sitting in front of it noticing that the code is cut off at the bottom or that the
 * event's name has pushed it off the table. It is furniture, and the first person to discover
 * it is wrong is a guest whose phone will not focus on it.
 *
 * So the last test does what a guest's phone does: takes the pixels of the whole screen, as
 * composed, and decodes them.
 */
class DisplayRenderTest {
    @Test
    fun `renders the list of events to choose from`() {
        render("display-choosing", width = 1_600, height = 2_400) {
            DisplayUiState(
                content =
                    UiState.Success(
                        DisplayContent(
                            studioName = "Harbourline Photography",
                            events =
                                listOf(
                                    DisplayableEvent("event-1", "Harbour Awards 2026", startsAt = null),
                                    DisplayableEvent("event-2", "Saturday walk-ups", startsAt = null),
                                ),
                        ),
                    ),
            )
        }
    }

    /**
     * The state a studio meets before it has opened anything, on a device that cannot fix it.
     *
     * Rendered deliberately: the equivalent screen elsewhere in this project was a dead end
     * for a month because nobody looked at the empty case, and here the remedy is not even on
     * this device — it is on a laptop across the room, so the screen has to say so.
     */
    @Test
    fun `renders having nothing to show`() {
        render("display-nothing-open", width = 1_600, height = 2_400) {
            DisplayUiState(content = UiState.Empty)
        }
    }

    @Test
    fun `renders the code on the table`() {
        render("display-showing", width = 1_600, height = 2_400) {
            DisplayUiState(content = UiState.Success(content(showing = showing())))
        }
    }

    /** Landscape, which is how a device propped in a stand actually sits. */
    @Test
    fun `renders the code on its side`() {
        render("display-showing-landscape", width = 2_400, height = 1_600) {
            DisplayUiState(content = UiState.Success(content(showing = showing())))
        }
    }

    /**
     * The code withdrawn from the laptop across the room.
     *
     * Worth looking at because it is the state where the screen has to stop being useful and
     * say why — and because a screen that merely removes the code looks identical to one that
     * has crashed.
     */
    @Test
    fun `renders a withdrawn code`() {
        render("display-withdrawn", width = 1_600, height = 2_400) {
            DisplayUiState(content = UiState.Success(content(showing = showing().copy(withdrawn = true))))
        }
    }

    @Test
    fun `renders the password prompt`() {
        render("display-unlocking", width = 1_600, height = 2_400) {
            DisplayUiState(
                content =
                    UiState.Success(
                        content(
                            showing =
                                showing().copy(
                                    unlock =
                                        Unlock(
                                            password = "wrong",
                                            problem = "That is not the password for this studio.",
                                        ),
                                ),
                        ),
                    ),
            )
        }
    }

    /**
     * A phone's job, done by the test.
     *
     * Every other assertion about this screen is about what it contains. This one is about
     * whether the thing it exists for actually works: the code, at the size the layout gives
     * it, among everything else on the screen, still reads as the link it was made from.
     *
     * A layout change that shrank the code, or a name long enough to squeeze it, would pass
     * every other test here and fail at a table.
     */
    @Test
    fun `the code on the screen decodes back to the link`() {
        val link = "https://yellowtrackphotos.com/join/nvmQ9xkkfDjk12Jx7kpKkA"
        val rows = matrixFor(link)

        // Both orientations, because the code is sized off the shorter side and the two
        // arrangements give it different room.
        listOf(1_600 to 2_400, 2_400 to 1_600).forEach { (width, height) ->
            val pixels =
                pixels(width, height) {
                    DisplayUiState(
                        content =
                            UiState.Success(
                                content(
                                    showing =
                                        Showing(
                                            event = DisplayableEvent("event-1", "Harbour Awards 2026", null),
                                            code = QrMatrix(size = rows.size, rows = rows),
                                            link = link,
                                        ),
                                ),
                            ),
                    )
                }

            assertEquals(link, decode(pixels, width, height), "the code did not read at $width by $height")
        }
    }

    // -- Plumbing -----------------------------------------------------------------------------

    private fun content(showing: Showing) =
        DisplayContent(
            studioName = "Harbourline Photography",
            events = listOf(DisplayableEvent("event-1", "Harbour Awards 2026", startsAt = null)),
            showing = showing,
        )

    private fun showing() =
        Showing(
            event = DisplayableEvent("event-1", "Harbour Awards 2026", startsAt = null),
            code = QrMatrix(size = 25, rows = matrixFor("https://yellowtrackphotos.com/join/abc")),
            link = "https://yellowtrackphotos.com/join/nvmQ9xkkfDjk12Jx7kpKkA",
        )

    /** A real encoding, produced the way the server produces it. */
    private fun matrixFor(content: String): List<String> {
        val matrix =
            QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                1,
                1,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN to 4,
                    EncodeHintType.CHARACTER_SET to "ISO-8859-1",
                ),
            )

        return (0 until matrix.height).map { y ->
            buildString { (0 until matrix.width).forEach { x -> append(if (matrix[x, y]) '1' else '0') } }
        }
    }

    private fun decode(
        pixels: IntArray,
        width: Int,
        height: Int,
    ): String = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))).text

    @OptIn(ExperimentalComposeUiApi::class)
    private fun pixels(
        width: Int,
        height: Int,
        state: () -> DisplayUiState,
    ): IntArray {
        val bytes = encode(width, height, density = 1f, state = state)
        val image = Image.makeFromEncoded(bytes)
        val bitmap = org.jetbrains.skia.Bitmap()
        bitmap.allocN32Pixels(image.width, image.height)
        image.readPixels(bitmap)

        return IntArray(image.width * image.height) { i ->
            bitmap.getColor(i % image.width, i / image.width) and 0xFFFFFF
        }
    }

    private fun render(
        name: String,
        width: Int,
        height: Int,
        state: () -> DisplayUiState,
    ) {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, "$name.png")

        target.writeBytes(encode(width, height, density = 2f, state = state))

        assertTrue(target.length() > 0, "nothing was written to ${target.absolutePath}")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun encode(
        width: Int,
        height: Int,
        density: Float,
        state: () -> DisplayUiState,
    ): ByteArray {
        val scene =
            ImageComposeScene(width = width, height = height, density = Density(density)) {
                YellowTrackTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = YTTheme.colors.background) {
                        DisplayScreen(
                            uiState = state(),
                            onShow = {},
                            onRetry = {},
                            onAskToLeave = {},
                            onCancelLeaving = {},
                            onTypePassword = {},
                            onConfirmUnlock = {},
                            onDismissProblem = {},
                        )
                    }
                }
            }

        return try {
            // Rendered twice, the second time half a second in.
            //
            // A scene renders one frame, and a dialog fades in — so frame zero catches it at
            // whatever opacity the animation starts at. Every dialog rendered in this project
            // before now came out ghosted and unreadable for that reason, which is a poor
            // result for a test whose whole purpose is to be looked at. Advancing the clock
            // lets the animation finish.
            scene.render(0L)
            scene.render(SETTLED_NANOS).encodeToData()!!.bytes
        } finally {
            scene.close()
        }
    }

    /** Longer than any enter animation in the design system, and nothing here loops. */
    private companion object {
        const val SETTLED_NANOS = 500_000_000L
    }
}
