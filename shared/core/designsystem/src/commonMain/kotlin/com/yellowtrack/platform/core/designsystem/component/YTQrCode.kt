package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.floor

/**
 * A sign-up code, drawn.
 *
 * The geometry comes from the server, which owns the one QR encoder in this project — the
 * printed card and this draw the same code, at the same error correction, with the same quiet
 * zone. Encoding is Reed-Solomon and masking; drawing is a loop, and only the loop is here.
 *
 * ## Why the module size is floored
 *
 * A code is a grid of equal squares, and a reader locates it by finding that regularity. If
 * modules land on fractional pixels the renderer antialiases their edges, and a phone reading
 * it at an angle in bad light has to guess where each boundary is. So each module is a whole
 * number of pixels and the remainder becomes margin, which costs a few pixels of size and buys
 * a code that scans on the first try rather than the third.
 *
 * ## Always black on white
 *
 * Not themed, deliberately. A code inverted for a dark theme is one many older readers refuse,
 * and one a phone camera meets far less often — and this is a thing a stranger points a phone
 * at once, in a hurry, in whatever lighting the venue has.
 */
@Composable
fun YTQrCode(
    /** One string per row, `1` for a dark module — as `QrMatrix` carries it. */
    rows: List<String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Sign-up code",
    /**
     * The modules, which must be the darker of the two.
     *
     * Readers expect dark on light and many refuse the inverse outright, so this is not a
     * free choice: a brand's colour can be [light] or it can be this one, and if it is a pale
     * colour it can only be [light].
     */
    dark: Color = Color.Black,
    /** What the modules sit on. Painted rather than assumed — see below. */
    light: Color = Color.White,
    /**
     * Drawn over the middle, or nothing.
     *
     * Costs error correction. The code is encoded at level `H`, which recovers about thirty
     * per cent of itself, and every module a logo covers is spent out of that budget — the
     * same budget that absorbs glare, a thumbprint and a bad angle at a table. So this is
     * deliberately small, and [LOGO_FRACTION_OF_WIDTH] says how small and why.
     */
    logo: Painter? = null,
    /**
     * How much of the code's width [logo] may cover, as a fraction.
     *
     * Exposed because it is the number that decides whether the code still scans, so it
     * should be visible at the call site rather than buried. Raise it and measure: the test
     * beside this component decodes the result at every size, with the mark blacked out.
     */
    logoFraction: Float = LOGO_FRACTION_OF_WIDTH,
    /**
     * What [logo] is drawn onto, inside the code.
     *
     * Defaults to [light], which suits a mark that is dark. A mark the same colour as the
     * field disappears into it — the launcher icon learned this and says so in `colors.xml`:
     * a yellow mark on yellow is a yellow square. Give it the dark colour and the mark reads
     * as a plate, the way the icon does.
     */
    logoPlate: Color = light,
) {
    if (rows.isEmpty()) return

    val size = rows.size

    Canvas(
        modifier =
            modifier
                .aspectRatio(1f)
                .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        // Whole pixels per module, and whatever is left over spread as margin. The quiet zone
        // is already in the matrix, so this only adds to it — which is never wrong.
        val module = floor(minOf(this.size.width, this.size.height) / size)
        if (module < 1f) return@Canvas

        val drawn = module * size

        // The origin is floored as well as the module size.
        //
        // Flooring only the module size leaves a centring offset that can land on a half
        // pixel — at which point every module straddles a boundary and is antialiased, which
        // is the thing whole modules were meant to avoid. Measured: with the offset
        // unfloored, a code rendered at 160 and 240 pixels decoded and the same code at 200
        // did not, because that width alone produced a fractional origin.
        //
        // Only the horizontal case is demonstrated by a test. Mutating `top` alone leaves the
        // code decodable, because the canvas is square and a fractional offset on one axis is
        // recoverable. It is floored anyway: the asymmetry would be an accident of the reader
        // rather than a property worth relying on.
        val left = floor((this.size.width - drawn) / 2f)
        val top = floor((this.size.height - drawn) / 2f)

        // The light background is painted rather than assumed: this is drawn onto whatever the
        // surrounding screen is, and a code needs its own field to be found against.
        drawRect(color = light, topLeft = Offset(left, top), size = Size(drawn, drawn))

        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, module_ ->
                if (module_ == '1') {
                    drawRect(
                        color = dark,
                        topLeft = Offset(left + x * module, top + y * module),
                        size = Size(module, module),
                    )
                }
            }
        }

        logo?.let { painter ->
            // Snapped to whole modules, like everything else here. A logo whose edges land
            // mid-module leaves slivers of half-covered modules around it, which is exactly
            // the ambiguity the flooring above exists to avoid.
            val logoModules = floor(size * logoFraction).toInt().coerceAtLeast(1)
            val padModules = logoModules + 2 * LOGO_MARGIN_MODULES
            val padSide = padModules * module
            val padLeft = left + floor((drawn - padSide) / (2f * module)) * module
            val padTop = top + floor((drawn - padSide) / (2f * module)) * module

            // Always a ring of the light colour first, whatever the plate is. Without it a
            // dark plate touches the dark modules around it and the two read as one blob,
            // which is a bigger piece of damage than the plate alone.
            drawRect(color = light, topLeft = Offset(padLeft, padTop), size = Size(padSide, padSide))

            val logoSide = logoModules * module
            val logoLeft = padLeft + LOGO_MARGIN_MODULES * module
            val logoTop = padTop + LOGO_MARGIN_MODULES * module

            if (logoPlate != light) {
                drawRect(color = logoPlate, topLeft = Offset(logoLeft, logoTop), size = Size(logoSide, logoSide))
            }

            // Inset inside the plate so the mark is not flush against its own edge. It read
            // as clipped rather than as placed, which is the kind of thing that only shows up
            // when somebody looks at it.
            val inset = logoSide * LOGO_INSET_FRACTION

            // Clipped to the plate, so a painter cannot draw outside the square it was given.
            //
            // `Painter.draw` is asked for a size; it is not obliged to stay within one, and
            // what a given painter does with a size it cannot honour — an intrinsic aspect it
            // wants to keep, a nine-patch, a vector with its own viewport — is its business
            // rather than this component's. A mark that spilled past the plate would cover
            // modules the error correction has not budgeted for, and it would do so quietly:
            // the code would still look like a code.
            // Fitted rather than filled, so the mark keeps its own proportions.
            //
            // The plate is square because the code's modules are; a mark is whatever shape it
            // is. Drawing it at the plate's size stretched it to fit — which nothing here
            // could see, because every test drew a shape that looks the same stretched.
            val available = logoSide - 2 * inset
            val intrinsic = painter.intrinsicSize
            val markSize =
                if (intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
                    val scale = minOf(available / intrinsic.width, available / intrinsic.height)
                    Size(intrinsic.width * scale, intrinsic.height * scale)
                } else {
                    // A painter with no opinion gets the square, which is all there is to give
                    // it.
                    Size(available, available)
                }

            clipRect(
                left = logoLeft,
                top = logoTop,
                right = logoLeft + logoSide,
                bottom = logoTop + logoSide,
            ) {
                translate(
                    left = logoLeft + inset + (available - markSize.width) / 2f,
                    top = logoTop + inset + (available - markSize.height) / 2f,
                ) {
                    with(painter) { draw(markSize) }
                }
            }
        }
    }
}

/**
 * How much of the code's width the mark may cover.
 *
 * Chosen by decoding rather than by taste. A logo is contiguous damage in the middle of the
 * code, which error correction handles worse than the same number of modules scattered about,
 * and the budget it spends is the budget that would otherwise absorb a venue's lighting.
 *
 * A fifth of the width is about four per cent of the area, well inside what level `H` allows,
 * and it decodes at every size this is drawn at with the whole mark blacked out — which is a
 * harsher test than the real mark, since that has transparency the reader can see through.
 */
private const val LOGO_FRACTION_OF_WIDTH = 0.2f

/** Blank modules between the mark and the code, so the two cannot be read as one. */
private const val LOGO_MARGIN_MODULES = 1

/** Breathing room between the mark and the edge of its plate. */
private const val LOGO_INSET_FRACTION = 0.12f
