package com.yellowtrack.platform.core.export

/**
 * Renders a sheet as plain text.
 *
 * This is the version that actually reaches people. A second shooter gets sent a message,
 * not an attachment, and a call sheet that has to be downloaded and opened is one that is
 * read at the venue rather than the night before.
 *
 * Nothing is padded into columns. Aligned text survives a desktop mail client and falls
 * apart in a phone's message app, which is where this is read.
 */
fun Sheet.toPlainText(): String =
    buildString {
        appendLine(title)
        subtitle?.let { appendLine(it) }

        sections.forEach { section ->
            appendLine()
            appendLine(section.heading.uppercase())
            section.blocks.forEach { appendBlock(it) }
        }

        footer?.let {
            appendLine()
            appendLine(it)
        }
    }.trimEnd() + "\n"

private fun StringBuilder.appendBlock(block: SheetBlock) {
    when (block) {
        is SheetBlock.Facts -> block.facts.forEach { appendLine("${it.label}: ${it.value}") }

        is SheetBlock.Entries ->
            block.entries.forEach { entry ->
                // Name and time on one line, because that is the pair being looked for.
                appendLine(listOfNotNull(entry.name, entry.trailing).joinToString(" — "))
                entry.detail?.let { appendLine("  $it") }
            }

        is SheetBlock.Checklist ->
            block.groups.forEach { group ->
                if (group.name.isNotBlank()) appendLine(group.name)
                group.items.forEach { appendLine("[ ] $it") }
            }

        is SheetBlock.Lines -> block.lines.forEach { appendLine(it) }

        is SheetBlock.Paragraphs -> block.paragraphs.forEach { appendLine(it) }

        is SheetBlock.Absent -> appendLine(block.message)
    }
}
