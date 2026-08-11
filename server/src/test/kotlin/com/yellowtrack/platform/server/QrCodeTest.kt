package com.yellowtrack.platform.server

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.yellowtrack.platform.server.event.QrCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The code is read back, not merely produced.
 *
 * A QR generator that emits a plausible-looking grid of squares which no phone can decode is
 * the exact shape of failure this project keeps finding: it renders, it looks right, and it
 * fails in somebody's hand at an event. So these tests decode the output and compare it to
 * what went in.
 *
 * The SVG is rasterised here by hand — a loop over the path, which is the same loop that
 * wrote it — rather than by pulling in an SVG renderer. That keeps the test honest about the
 * one thing it can check without a browser: the modules are where the encoder said.
 */
class QrCodeTest {
    @Test
    fun `a link survives being turned into a code and read back`() {
        val link = "https://yellowtrackphotos.com/join/sRzW2qICHRoYGE9UXL5X0w"

        assertEquals(link, decode(QrCode.svg(link)))
    }

    /** Tokens are base64url, so the two characters that are not letters or digits matter. */
    @Test
    fun `a token containing dashes and underscores survives`() {
        val link = "https://yellowtrackphotos.com/join/qaQZfhAPGigc-nCKRzbdlA_x-y_z"

        assertEquals(link, decode(QrCode.svg(link)))
    }

    /** A longer host and token still encodes, at whatever version zxing needs. */
    @Test
    fun `a long link still encodes`() {
        val link = "https://photographs.a-rather-longer-domain-name.example.com/join/" + "A".repeat(40)

        assertEquals(link, decode(QrCode.svg(link)))
    }

    /**
     * The border is not decoration.
     *
     * Readers find a code by its edges against the surface it was printed on. Without a quiet
     * zone a code that looks perfect on screen fails on paper, which is the worst place to
     * discover it.
     */
    @Test
    fun `the code keeps a quiet zone around it`() {
        val svg = QrCode.svg("https://yellowtrackphotos.com/join/abc", quietZone = 4)
        val modules = rasterise(svg)
        val side = modules.size

        // The outer four rings are blank on every side.
        (0 until 4).forEach { ring ->
            (0 until side).forEach { i ->
                assertTrue(!modules[ring][i], "row $ring is not blank")
                assertTrue(!modules[side - 1 - ring][i], "row ${side - 1 - ring} is not blank")
                assertTrue(!modules[i][ring], "column $ring is not blank")
                assertTrue(!modules[i][side - 1 - ring], "column ${side - 1 - ring} is not blank")
            }
        }
    }

    @Test
    fun `the output is an svg that scales`() {
        val svg = QrCode.svg("https://yellowtrackphotos.com/join/abc")

        assertTrue(svg.startsWith("<svg"), svg.take(60))
        // A viewBox is what makes it printable at any size; width and height alone would pin
        // it to whatever this server happened to choose.
        assertTrue("viewBox=\"0 0 " in svg, "no viewBox — it would not scale")
        assertTrue("crispEdges" in svg, "without this a renderer antialiases the modules")
    }

    /**
     * The error correction is not decoration.
     *
     * This code is photographed at an angle, in a venue's lighting, with something resting
     * against a corner of it, on paper that has been handled all day. Level `H` recovers from
     * roughly thirty per cent of it being unreadable, and a code that fails to scan is a guest
     * who walks away.
     *
     * A clean render decodes at any error-correction level, so a decode test alone says
     * nothing about this — the level was dropped to `L` in a mutation and every other test
     * still passed. Obscuring part of the code is what makes the choice observable.
     */
    @Test
    fun `a code with a corner obliterated still reads`() {
        val link = "https://yellowtrackphotos.com/join/sRzW2qICHRoYGE9UXL5X0w"
        val modules = rasterise(QrCode.svg(link))
        val side = modules.size

        // A block over the lower-right quadrant, which holds data rather than a finder
        // pattern — obscuring a finder would stop a reader locating the code at all, which is
        // a different failure and not one error correction is for.
        val from = side * 6 / 10
        val to = side * 9 / 10
        for (y in from until to) {
            for (x in from until to) {
                modules[y][x] = false
            }
        }

        val obscured = ((to - from) * (to - from)).toDouble() / (side * side)
        assertTrue(obscured > 0.07, "the damage is too small to distinguish H from L: $obscured")

        assertEquals(link, decode(modules), "the code did not survive being partly obscured")
    }

    // -- The same code as a grid ---------------------------------------------------------------

    /**
     * The grid and the SVG are one code, module for module.
     *
     * They share an encoder, so this looks tautological — and is not. Each has its own loop
     * turning the encoder's output into its own shape, and those loops are exactly where the
     * two drift apart. Transposing one of them produces a grid that still decodes, because a
     * reader will try a mirrored code, so a decode test cannot see the difference at all. This
     * compares the modules.
     */
    @Test
    fun `the grid is the same code as the printed one`() {
        val link = "https://yellowtrackphotos.com/join/sRzW2qICHRoYGE9UXL5X0w"
        val printed = rasterise(QrCode.svg(link))

        val grid = QrCode.matrix(link)

        assertEquals(printed.size, grid.size, "the two are not even the same size")
        val differing =
            (0 until grid.size).flatMap { y ->
                (0 until grid.size).mapNotNull { x ->
                    "$x,$y".takeIf { printed[y][x] != (grid.rows[y][x] == '1') }
                }
            }

        assertEquals(emptyList(), differing.take(5), "the screen code differs from the printed one here")
    }

    /**
     * The border survives the second rendering too.
     *
     * A decode test cannot catch this either: given a clean synthetic image a reader finds a
     * code flush to the edge quite happily. A phone pointed at a screen, against whatever is
     * behind it, does not.
     */
    @Test
    fun `the grid keeps a quiet zone around it`() {
        val grid = QrCode.matrix("https://yellowtrackphotos.com/join/abc", quietZone = 4)

        val side = grid.size
        (0 until 4).forEach { ring ->
            (0 until side).forEach { i ->
                assertTrue(grid.rows[ring][i] == '0', "row $ring is not blank")
                assertTrue(grid.rows[side - 1 - ring][i] == '0', "row ${side - 1 - ring} is not blank")
                assertTrue(grid.rows[i][ring] == '0', "column $ring is not blank")
                assertTrue(grid.rows[i][side - 1 - ring] == '0', "column ${side - 1 - ring} is not blank")
            }
        }
    }

    // -- Reading it back ----------------------------------------------------------------------

    /** The path is `M x yh1v1h-1z` per dark module, which is what wrote it. */
    private fun rasterise(svg: String): Array<BooleanArray> {
        val side = Regex("""viewBox="0 0 (\d+) """).find(svg)!!.groupValues[1].toInt()
        val modules = Array(side) { BooleanArray(side) }

        Regex("""M(\d+) (\d+)h1v1h-1z""").findAll(svg).forEach { match ->
            val (x, y) = match.destructured
            modules[y.toInt()][x.toInt()] = true
        }

        return modules
    }

    private fun decode(svg: String): String = decode(rasterise(svg))

    /** Blows the modules up so the binarizer has something to work with, then decodes. */
    private fun decode(modules: Array<BooleanArray>): String {
        val side = modules.size
        val scale = 4
        val pixels = IntArray(side * scale * side * scale)

        for (y in 0 until side * scale) {
            for (x in 0 until side * scale) {
                val dark = modules[y / scale][x / scale]
                pixels[y * side * scale + x] = if (dark) 0x000000 else 0xFFFFFF
            }
        }

        val bitmap =
            BinaryBitmap(
                HybridBinarizer(RGBLuminanceSource(side * scale, side * scale, pixels)),
            )

        return QRCodeReader().decode(bitmap).text
    }
}
