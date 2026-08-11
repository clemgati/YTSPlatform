package com.yellowtrack.platform.server.event

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.yellowtrack.platform.core.model.event.QrMatrix

/**
 * A sign-up link as something a phone can read off a wall.
 *
 * ## SVG rather than an image
 *
 * This ends up printed — on a banner, a table card, a sheet of paper by a camera — and
 * nobody knows at what size. A QR code is pure geometry, so vector output stays sharp at any
 * of them, where a PNG has to guess a resolution and is wrong at least once.
 *
 * It also means no image library on the server: zxing produces a matrix of bits and the
 * loop below turns that into rectangles.
 *
 * ## Why the error correction is high
 *
 * The default would produce a smaller code. This one is going to be photographed at an angle,
 * in a venue's lighting, possibly with something resting against a corner of it — and a code
 * that fails to scan is a guest who walks away. `H` recovers from roughly thirty per cent of
 * the code being unreadable, and costs only a slightly denser pattern.
 */
object QrCode {
    /**
     * @param quietZone modules of blank margin. Four is the specification's minimum, and
     *   omitting it is the classic reason a code that looks fine will not scan: readers need
     *   the border to find the code's edges against whatever it was printed on.
     */
    fun svg(
        content: String,
        quietZone: Int = 4,
    ): String {
        val matrix = encode(content, quietZone)

        val side = matrix.width

        // One path rather than a rectangle per module: a code is a few hundred dark modules,
        // and a few hundred elements is a file no printer driver enjoys.
        val squares =
            buildString {
                for (y in 0 until matrix.height) {
                    for (x in 0 until side) {
                        if (matrix[x, y]) append("M$x ${y}h1v1h-1z")
                    }
                }
            }

        return """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $side $side"
                 width="$side" height="$side" shape-rendering="crispEdges"
                 role="img" aria-label="Sign-up code">
                <rect width="$side" height="$side" fill="#ffffff"/>
                <path d="$squares" fill="#000000"/>
            </svg>
            """.trimIndent()
    }

    /**
     * The same code as a grid, for a client that draws it rather than rendering markup.
     *
     * The printed card gets SVG, which a browser renders. An application showing a code on a
     * screen has no browser and no SVG renderer, and adding one to every platform to draw a
     * grid of squares would be absurd — so the server sends the grid.
     *
     * Shares [encode] with [svg] deliberately: a code on a screen and a code on paper are then
     * the same code, including the quiet zone and the error correction, both of which are easy
     * to lose when a second implementation draws the same thing.
     */
    fun matrix(
        content: String,
        quietZone: Int = 4,
    ): QrMatrix {
        val matrix = encode(content, quietZone)

        return QrMatrix(
            size = matrix.width,
            rows =
                (0 until matrix.height).map { y ->
                    buildString {
                        (0 until matrix.width).forEach { x -> append(if (matrix[x, y]) '1' else '0') }
                    }
                },
        )
    }

    /**
     * Sized in modules rather than pixels: whoever draws it decides how big, so these are the
     * smallest values that let zxing choose the version it needs.
     *
     * `H` error correction because this is photographed at an angle, in a venue's lighting,
     * off paper or off a screen with something reflecting in it. The margin is the quiet zone
     * readers use to find the code's edges, and the character set is stated because the link
     * is ASCII and saying so keeps the code smaller.
     */
    private fun encode(
        content: String,
        quietZone: Int,
    ) = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        1,
        1,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to quietZone,
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        ),
    )
}

/**
 * The code as something a studio prints and puts on a table.
 *
 * A bare SVG is the wrong artifact for the actual job: somebody has to know what the code is
 * for before they will point a phone at it. This carries the event's name, one line of
 * instruction, and the link in text — because a code photographs badly under some lighting
 * and a person who can read the URL can still sign up.
 *
 * HTML rather than PDF so it prints from any browser without this server growing a rendering
 * engine, and so a studio that wants it on a real banner can hand the file to a designer.
 */
object InviteCard {
    fun html(
        eventName: String,
        link: String,
    ): String =
        """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="utf-8">
            <title>${escape(eventName)} — sign-up code</title>
            <style>
                /* Centred on the page it is printed on, at a size that scans from a metre
                   away. `@page` margins rather than body padding: a printer ignores the
                   second and honours the first. */
                @page { margin: 18mm; }
                body {
                    margin: 0;
                    font: 16pt/1.4 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                    color: #16130d;
                    text-align: center;
                }
                h1 { font-size: 28pt; margin: 0 0 4mm; }
                p { margin: 0 0 6mm; }
                .code { width: 110mm; height: 110mm; margin: 0 auto 6mm; }
                .code svg { width: 100%; height: 100%; display: block; }
                /* Small, and monospaced so a `1` and an `l` are distinguishable to somebody
                   typing it off a printed page. */
                .link { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11pt; word-break: break-all; }
                .muted { color: #5f5849; font-size: 12pt; }
            </style>
        </head>
        <body>
            <h1>${escape(eventName)}</h1>
            <p>Scan this to get your photographs</p>
            <div class="code">${QrCode.svg(link)}</div>
            <p class="link">${escape(link)}</p>
            <p class="muted">
                We will email your photographs when the photographer has finished with them.
            </p>
        </body>
        </html>
        """.trimIndent()

    /** An event name is typed by a studio and printed on a page that goes on a table. */
    private fun escape(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
