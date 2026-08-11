package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
        // surrounding screen is, and a code needs its own white to be found against.
        drawRect(color = Color.White, topLeft = Offset(left, top), size = Size(drawn, drawn))

        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, module_ ->
                if (module_ == '1') {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(left + x * module, top + y * module),
                        size = Size(module, module),
                    )
                }
            }
        }
    }
}
