package com.yellowtrack.platform.core.export

/**
 * A printed sheet, described once and rendered more than one way.
 *
 * The structure exists so the HTML and the plain text cannot drift: both walk the same
 * blocks, so a fact added for the printed version cannot go missing from the version
 * pasted into a message.
 */
data class Sheet(
    val title: String,
    val subtitle: String?,
    val sections: List<SheetSection>,
    /**
     * The line at the bottom of everything — payment terms, a registration statement.
     *
     * Not a section, because rendering it as one would give it a heading and the weight
     * that comes with a heading. It is the small print, and it should look like it.
     */
    val footer: String? = null,
)

data class SheetSection(
    val heading: String,
    val blocks: List<SheetBlock>,
)

sealed interface SheetBlock {
    /** Label and value: where, when, what time to be there. */
    data class Facts(
        val facts: List<SheetFact>,
    ) : SheetBlock

    /** People or things, each with a name, a detail line, and something at the right. */
    data class Entries(
        val entries: List<SheetEntry>,
    ) : SheetBlock

    /** Things to be ticked off on the day, in the groups they are worked in. */
    data class Checklist(
        val groups: List<SheetGroup>,
    ) : SheetBlock

    /**
     * Lines that belong together — an address, bank details.
     *
     * Kept tight, because the line breaks in an address are part of the address. Rendered
     * as paragraphs it takes a third of a page and reads as seven unrelated facts.
     */
    data class Lines(
        val lines: List<String>,
    ) : SheetBlock

    /** Prose, each entry its own paragraph. */
    data class Paragraphs(
        val paragraphs: List<String>,
    ) : SheetBlock

    /**
     * Said when a section would otherwise be empty.
     *
     * A call sheet with a blank crew heading reads as a mistake; one that says "Nobody
     * else is booked for this day" reads as an answer.
     */
    data class Absent(
        val message: String,
    ) : SheetBlock
}

data class SheetFact(
    val label: String,
    val value: String,
    /** Draws the eye — the call time, and the light that decides when to shoot.  */
    val isEmphasised: Boolean = false,
)

data class SheetEntry(
    val name: String,
    val detail: String?,
    val trailing: String?,
)

data class SheetGroup(
    val name: String,
    val items: List<String>,
)
