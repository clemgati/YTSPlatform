package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toArgb
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

    /**
     * The code still reads with the mark on it, at every size, in the brand's colours.
     *
     * The mark is a solid opaque block here, which is harsher than the real one: it covers
     * every module beneath it, where the real mark has transparency a reader sees through.
     *
     * Measured when this was written: a block covering 30% of the width still decoded at
     * every size and 35% decoded at none. The component ships 20%, so this passes with about
     * ten points of width to spare — and if somebody raises that constant toward the cliff,
     * this is what fails.
     */
    @Test
    fun `a branded code with a mark on it decodes at every size`() {
        val link = "https://yellowtrackphotos.com/join/nvmQ9xkkfDjk12Jx7kpKkA"
        val rows = matrixFor(link)

        val failed =
            listOf(240, 320, 400, 512, 640, 900).filterNot { side ->
                val pixels =
                    render(
                        rows,
                        side,
                        background = Color(0xFF181818),
                        dark = BRAND_DARK,
                        light = BRAND_LIGHT,
                        logo = true,
                    )
                runCatching { decode(pixels) }.getOrNull() == link
            }

        assertEquals(emptyList(), failed, "the branded code did not decode at these pixel sizes")
    }

    /**
     * And it paints its own field rather than letting the screen show through.
     *
     * The white-background version of this test cannot cover the branded one: it asks whether
     * the light modules are near-white, and here they are deliberately not. What matters is
     * the same either way — that the light modules are the colour this component chose, not
     * whatever was behind it.
     */
    @Test
    fun `a branded code paints its own field`() {
        val link = "https://yellowtrackphotos.com/join/nvmQ9xkkfDjk12Jx7kpKkA"
        val pixels =
            render(
                matrixFor(link),
                480,
                background = Color(0xFF181818),
                dark = BRAND_DARK,
                light = BRAND_LIGHT,
                logo = true,
            )

        val field = pixels.count { it == (BRAND_LIGHT.toArgb() and 0xFFFFFF) }
        val fraction = field.toDouble() / pixels.size

        assertTrue(fraction > 0.25, "only ${(fraction * 100).toInt()}% of the code is the light colour")
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
        dark: Color = Color.Black,
        light: Color = Color.White,
        logo: Boolean = false,
    ): IntArray {
        val scene =
            ImageComposeScene(width = side, height = side, density = Density(1f)) {
                YTQrCode(
                    dark = dark,
                    light = light,
                    // A solid block, which is the worst case: it covers every module under
                    // it, where a real mark has transparency a reader can see through.
                    // Solid and opaque, which covers every module beneath it — worse than
                    // the real mark, which has transparency a reader can see through.
                    logo = if (logo) ColorPainter(Color.Black) else null,
                    logoPlate = if (logo) dark else light,
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

    private companion object {
        /** The palette the display app uses, and the only one the brand allows here. */
        val BRAND_DARK = Color(0xFF111111)
        val BRAND_LIGHT = Color(0xFFFAB91D)
    }
}
