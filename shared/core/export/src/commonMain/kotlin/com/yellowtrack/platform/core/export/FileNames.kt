package com.yellowtrack.platform.core.export

/**
 * Turns a title into a file name that survives every filesystem this ships on.
 *
 * Windows rejects `\ / : * ? " < > |`, macOS and Linux reject `/`, and a colon in a
 * date-and-time is the single most likely character to appear here. Rather than banning a
 * list, only characters known to be safe are kept — the set cannot grow a hole when the
 * app reaches a filesystem nobody tested.
 *
 * Accents are kept: a shoot for "Café Lumière" should not be filed under "caf-lumi-re".
 * They are legal in every filesystem in use, and the studio has to recognise the name.
 */
fun slugify(text: String): String {
    val slug =
        text
            .lowercase()
            .map { character ->
                when {
                    character.isLetterOrDigit() -> character
                    else -> '-'
                }
            }.joinToString("")
            .replace(HYPHEN_RUN, "-")
            .trim('-')

    // A name made entirely of punctuation would otherwise produce an empty file name,
    // which fails on write rather than at the point the name was chosen.
    return slug.ifBlank { "document" }
}

private val HYPHEN_RUN = Regex("-+")
