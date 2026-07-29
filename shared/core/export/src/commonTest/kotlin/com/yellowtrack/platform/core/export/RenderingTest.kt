package com.yellowtrack.platform.core.export

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two renderings of a sheet.
 *
 * They walk the same blocks on purpose: a fact added for the printed version must not go
 * missing from the version pasted into a message, and these tests are what holds that.
 */
class RenderingTest {
    private val sheet =
        Sheet(
            title = "Call sheet — Wedding day",
            subtitle = "Smith & Jones",
            sections =
                listOf(
                    SheetSection(
                        heading = "The day",
                        blocks =
                            listOf(
                                SheetBlock.Facts(
                                    listOf(
                                        SheetFact("Date", "Saturday, August 15"),
                                        SheetFact("Call time", "12:30 PM", isEmphasised = true),
                                    ),
                                ),
                            ),
                    ),
                    SheetSection(
                        heading = "Crew",
                        blocks =
                            listOf(
                                SheetBlock.Entries(
                                    listOf(
                                        SheetEntry("Priya Shah", "Hair & make-up · 07700 900123", "9:00 AM"),
                                        SheetEntry("Alex Reed", "Videographer", null),
                                    ),
                                ),
                            ),
                    ),
                    SheetSection(
                        heading = "Shot list",
                        blocks =
                            listOf(
                                SheetBlock.Checklist(
                                    listOf(SheetGroup("Bride's family", listOf("Bride with both parents"))),
                                ),
                            ),
                    ),
                ),
        )

    // --- Both renderings ---------------------------------------------------------------------

    @Test
    fun `every fact on the printed sheet is on the one pasted into a message`() {
        val html = sheet.toHtml()
        val text = sheet.toPlainText()

        listOf("Saturday, August 15", "12:30 PM", "Priya Shah", "07700 900123", "Bride with both parents")
            .forEach { value ->
                assertTrue(html.contains(value), "missing from the HTML: $value")
                assertTrue(text.contains(value), "missing from the text: $value")
            }
    }

    // --- HTML ----------------------------------------------------------------------------------

    @Test
    fun `a client with an ampersand in their name does not break the page`() {
        val html = sheet.toHtml()

        assertTrue(html.contains("Smith &amp; Jones"))
        assertFalse(html.contains("Smith & Jones"), "a bare ampersand is not valid HTML")
    }

    @Test
    fun `angle brackets in a name are escaped rather than rendered as markup`() {
        val html =
            sheet
                .copy(subtitle = "<script>alert('x')</script>")
                .toHtml()

        assertFalse(html.contains("<script>"), "the sheet is opened in a browser by whoever receives it")
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `escaping the ampersand first does not mangle the entities it introduces`() {
        assertEquals("&amp;lt;", "&lt;".escapeHtml(), "a literal \"&lt;\" must survive as text")
    }

    @Test
    fun `the page is self-contained`() {
        val html = sheet.toHtml()

        // It is opened at a venue with no signal, on a phone that has only the attachment.
        listOf("http://", "https://", "<script", "<img").forEach { external ->
            assertFalse(html.contains(external, ignoreCase = true), "found $external")
        }
        assertTrue(html.contains("<style>"), "the styles have to travel with it")
    }

    @Test
    fun `shots print as boxes to tick rather than bullets`() {
        assertTrue(sheet.toHtml().contains("&#9744;"), "this gets printed and worked through with a pen")
    }

    // --- Plain text ------------------------------------------------------------------------------

    @Test
    fun `headings are legible without any markup`() {
        val text = sheet.toPlainText()

        assertTrue(text.contains("THE DAY"))
        assertTrue(text.contains("CREW"))
    }

    @Test
    fun `a person and their time read as one line`() {
        assertTrue(sheet.toPlainText().contains("Priya Shah — 9:00 AM"))
    }

    @Test
    fun `someone with no time is not left with a dangling separator`() {
        val text = sheet.toPlainText()

        assertTrue(text.contains("\nAlex Reed\n"), "was:\n$text")
    }

    @Test
    fun `the text ends with exactly one newline`() {
        val text = sheet.toPlainText()

        assertTrue(text.endsWith("\n"))
        assertFalse(text.endsWith("\n\n"), "trailing blank lines survive into whatever it is pasted into")
    }

    // --- File names --------------------------------------------------------------------------------

    @Test
    fun `a title becomes a name every filesystem accepts`() {
        assertEquals("call-sheet-johnson-wedding", slugify("Call sheet — Johnson Wedding"))
    }

    @Test
    fun `a colon does not reach a windows filesystem`() {
        assertEquals("wedding-2026-08-15-14-00", slugify("Wedding 2026-08-15 14:00"))
    }

    @Test
    fun `accents survive because the studio has to recognise the name`() {
        assertEquals("café-lumière", slugify("Café Lumière"))
    }

    @Test
    fun `a title of nothing but punctuation still produces a usable name`() {
        assertEquals("document", slugify("!!! ??? ---"))
        assertEquals("document", slugify(""))
    }

    @Test
    fun `a document names itself with its own extension`() {
        val document = Document("call-sheet-johnson-wedding", DocumentFormat.Html, "<html></html>")

        assertEquals("call-sheet-johnson-wedding.html", document.fileName)
    }
}
