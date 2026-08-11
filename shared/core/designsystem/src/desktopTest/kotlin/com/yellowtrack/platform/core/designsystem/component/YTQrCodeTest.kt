package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The code is read back off the canvas, not merely painted onto it.
 *
 * A component that draws a plausible grid of squares which no phone can decode is the exact
 * failure this is for: it renders, it looks like a QR code, and it fails in a stranger's hand
 * at an event. So these tests render with Compose, take the pixels, and hand them to a reader.
 *
 * The matrices come from a real encoding rather than being invented here — an invented grid
 * would prove the loop draws squares and nothing about whether the result is a code.
 */
class YTQrCodeTest {
    @Test
    fun `a rendered code decodes back to its link`() {
        val link = "https://yellowtrackphotos.com/join/nvmQ9xkkfDjk12Jx7kpKkA"

        assertEquals(link, decode(render(matrixFor(link), 480)))
    }

    /**
     * Every size, not one convenient size.
     *
     * This is the test that found the bug. With the origin unfloored, a code rendered at 160
     * and 240 pixels decoded and the same code at 200 did not — because that width alone put
     * the centring offset on a half pixel, and every module then straddled a boundary. One
     * size would have passed and shipped a component that fails to scan at some window widths
     * and not others, which is the worst kind of intermittent.
     */
    @Test
    fun `a code decodes at every size it might be drawn at`() {
        val link = "https://yellowtrackphotos.com/join/nvmQ9xkkfDjk12Jx7kpKkA"
        val rows = matrixFor(link)

        val failed =
            listOf(160, 200, 240, 280, 320, 360, 400, 512, 640, 900).filterNot { side ->
                runCatching { decode(render(rows, side)) }.getOrNull() == link
            }

        assertEquals(emptyList(), failed, "the code did not decode at these pixel sizes")
    }

    /** And large, which is what a tablet propped on a table actually shows. */
    @Test
    fun `a large rendering still decodes`() {
        val link = "https://yellowtrackphotos.com/join/nvmQ9xkkfDjk12Jx7kpKkA"

        assertEquals(link, decode(render(matrixFor(link), 900)))
    }

    /**
     * Drawn onto a dark screen, which is where this lives.
     *
     * The component paints its own white behind the modules. Without that the dark theme shows
     * through the light squares and the code is invisible to a reader — while looking, to a
     * person, like a slightly odd pattern.
     */
    @Test
    fun `a code on a dark background still decodes`() {
        val link = "https://yellowtrackphotos.com/join/nvmQ9xkkfDjk12Jx7kpKkA"

        assertEquals(link, decode(render(matrixFor(link), 480, background = Color(0xFF16130D))))
    }

    /**
     * Decoding is not the same as being visible, and this test exists because that difference
     * shipped a bug past the test above.
     *
     * With the white rectangle removed, the light modules were the dark theme showing through
     * and the code was near-black on near-black — and every decode test still passed. zxing is
     * given perfect pixels and a local threshold, so it recovers a code from a contrast ratio
     * of about 1.1:1 that no phone camera in a venue's lighting could ever find.
     *
     * So this measures the thing a camera needs rather than the thing a decoder tolerates: the
     * light modules must actually be light. A quarter of the surface is a floor well under the
     * roughly forty-five per cent a real code covers, and far above the nothing a missing
     * background gives.
     */
    @Test
    fun `the light modules are light even on a dark screen`() {
        val link = "https://yellowtrackphotos.com/join/nvmQ9xkkfDjk12Jx7kpKkA"
        val pixels = render(matrixFor(link), 480, background = Color(0xFF16130D))

        val light = pixels.count { luminance(it) > 0.85 }
        val fraction = light.toDouble() / pixels.size

        assertTrue(
            fraction > 0.25,
            "only ${(fraction * 100).toInt()}% of the code is light — a camera would see one dark square",
        )
    }

    /** Rec. 709, which is what a luminance source uses to decide light from dark. */
    private fun luminance(rgb: Int): Double {
        val r = (rgb shr 16 and 0xFF) / 255.0
        val g = (rgb shr 8 and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    // -- Rendering and reading ------------------------------------------------------------

    /** A real encoding, produced the way the server produces it. */
    private fun matrixFor(content: String): List<String> {
        val writer =
            com.google.zxing.qrcode
                .QRCodeWriter()
        val matrix =
            writer.encode(
                content,
                com.google.zxing.BarcodeFormat.QR_CODE,
                1,
                1,
                mapOf(
                    com.google.zxing.EncodeHintType.ERROR_CORRECTION to
                        com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H,
                    com.google.zxing.EncodeHintType.MARGIN to 4,
                    com.google.zxing.EncodeHintType.CHARACTER_SET to "ISO-8859-1",
                ),
            )

        return (0 until matrix.height).map { y ->
            buildString { (0 until matrix.width).forEach { x -> append(if (matrix[x, y]) '1' else '0') } }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        rows: List<String>,
        side: Int,
        background: Color = Color.White,
    ): IntArray {
        val scene =
            ImageComposeScene(width = side, height = side, density = Density(1f)) {
                YTQrCode(
                    rows = rows,
                    // Padding, because a code drawn hard against the edge of a surface has no
                    // margin beyond its own quiet zone on a real screen either.
                    modifier = Modifier.fillMaxSize().background(background).padding(8.dp),
                )
            }

        val bytes =
            try {
                scene.render().encodeToData()!!.bytes
            } finally {
                scene.close()
            }
        val image = Image.makeFromEncoded(bytes)
        val bitmap = org.jetbrains.skia.Bitmap()
        bitmap.allocN32Pixels(image.width, image.height)
        image.readPixels(bitmap)

        return IntArray(image.width * image.height) { i ->
            val x = i % image.width
            val y = i / image.width
            bitmap.getColor(x, y) and 0xFFFFFF
        }
    }

    private fun decode(pixels: IntArray): String {
        val side = kotlin.math.sqrt(pixels.size.toDouble()).toInt()
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(side, side, pixels)))

        return QRCodeReader().decode(bitmap).text
    }
}
