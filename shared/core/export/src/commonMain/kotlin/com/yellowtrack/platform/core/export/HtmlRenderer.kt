package com.yellowtrack.platform.core.export

/**
 * Renders a sheet as a self-contained web page.
 *
 * Self-contained is the requirement, not a preference: the file is going to be attached to
 * a message and opened on a phone with no connection at a venue with no signal. Nothing is
 * fetched — the styles are inline, there is no script, and there are no images.
 *
 * The print rules exist because "print to PDF" from a browser is how this becomes a PDF
 * without shipping a PDF library on four platforms.
 */
fun Sheet.toHtml(): String =
    buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"en\">")
        appendLine("<head>")
        appendLine("<meta charset=\"utf-8\">")
        appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        appendLine("<title>${title.escapeHtml()}</title>")
        appendLine("<style>$STYLES</style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("<h1>${title.escapeHtml()}</h1>")
        subtitle?.let { appendLine("<p class=\"subtitle\">${it.escapeHtml()}</p>") }

        sections.forEach { section ->
            appendLine("<section>")
            appendLine("<h2>${section.heading.escapeHtml()}</h2>")
            section.blocks.forEach { appendBlock(it) }
            appendLine("</section>")
        }

        appendLine("</body>")
        appendLine("</html>")
    }

private fun StringBuilder.appendBlock(block: SheetBlock) {
    when (block) {
        is SheetBlock.Facts -> {
            appendLine("<dl>")
            block.facts.forEach { fact ->
                val emphasis = if (fact.isEmphasised) " class=\"emphasis\"" else ""
                appendLine("<dt>${fact.label.escapeHtml()}</dt>")
                appendLine("<dd$emphasis>${fact.value.escapeHtml()}</dd>")
            }
            appendLine("</dl>")
        }

        is SheetBlock.Entries -> {
            appendLine("<ul class=\"entries\">")
            block.entries.forEach { entry ->
                appendLine("<li>")
                appendLine("<span class=\"name\">${entry.name.escapeHtml()}</span>")
                entry.trailing?.let { appendLine("<span class=\"trailing\">${it.escapeHtml()}</span>") }
                entry.detail?.let { appendLine("<span class=\"detail\">${it.escapeHtml()}</span>") }
                appendLine("</li>")
            }
            appendLine("</ul>")
        }

        is SheetBlock.Checklist -> {
            block.groups.forEach { group ->
                if (group.name.isNotBlank()) appendLine("<h3>${group.name.escapeHtml()}</h3>")
                appendLine("<ul class=\"checklist\">")
                // A real box rather than a bullet: this gets printed and ticked with a pen.
                group.items.forEach { appendLine("<li>&#9744; ${it.escapeHtml()}</li>") }
                appendLine("</ul>")
            }
        }

        is SheetBlock.Lines -> block.lines.forEach { appendLine("<p>${it.escapeHtml()}</p>") }

        is SheetBlock.Absent -> appendLine("<p class=\"absent\">${block.message.escapeHtml()}</p>")
    }
}

/**
 * Escapes text for HTML.
 *
 * A client called "Smith & Jones <Ltd>" is an ordinary client, not an attack, and the
 * sheet has to survive one. The ampersand is replaced first, or it would go on to mangle
 * the entities the other replacements introduce.
 */
internal fun String.escapeHtml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

private val STYLES =
    """
    :root { color-scheme: light; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      max-width: 44rem; margin: 0 auto; padding: 2rem 1.25rem 4rem;
      color: #16130d; background: #fff; line-height: 1.5;
    }
    h1 { font-size: 1.75rem; margin: 0 0 0.25rem; }
    h2 {
      font-size: 1rem; text-transform: uppercase; letter-spacing: 0.06em;
      margin: 2rem 0 0.5rem; padding-bottom: 0.35rem; border-bottom: 1px solid #d8d2c4;
    }
    h3 { font-size: 0.95rem; margin: 1rem 0 0.35rem; }
    .subtitle { margin: 0 0 0.5rem; color: #5c5647; }
    dl { display: grid; grid-template-columns: max-content 1fr; gap: 0.3rem 1.25rem; margin: 0; }
    dt { color: #5c5647; }
    dd { margin: 0; }
    dd.emphasis { font-weight: 600; }
    ul { list-style: none; margin: 0.25rem 0; padding: 0; }
    ul.entries li {
      display: grid; grid-template-columns: 1fr max-content;
      padding: 0.4rem 0; border-bottom: 1px solid #efebe1;
    }
    ul.entries .detail { grid-column: 1 / -1; color: #5c5647; font-size: 0.9rem; }
    ul.entries .trailing { text-align: right; font-weight: 600; }
    ul.checklist li { padding: 0.2rem 0; }
    .absent { color: #5c5647; }
    @media print {
      body { max-width: none; padding: 0; font-size: 11pt; }
      h2 { margin-top: 1.2rem; }
      section { break-inside: avoid; }
    }
    """.trimIndent()
