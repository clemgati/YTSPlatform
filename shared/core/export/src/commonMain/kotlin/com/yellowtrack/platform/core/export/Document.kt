package com.yellowtrack.platform.core.export

/**
 * How a rendered document is written out.
 *
 * Both are plain text on disk, which is the point: no PDF library, no per-platform
 * rendering, and a file that opens on a phone belonging to a second shooter who has never
 * heard of this application.
 */
enum class DocumentFormat(
    val extension: String,
    val mediaType: String,
) {
    /** Opens in any browser and prints to PDF from there. */
    Html("html", "text/html"),

    /** For pasting into a message, which is how a call sheet actually reaches people. */
    PlainText("txt", "text/plain"),
}

/**
 * A finished document, ready to be written somewhere.
 *
 * The content is a string rather than bytes because every format here is text, and a
 * string is what the clipboard takes — which is the fastest route to a second shooter's
 * phone and needs no filesystem at all.
 */
data class Document(
    /** Without an extension; [DocumentFormat] supplies that. */
    val baseName: String,
    val format: DocumentFormat,
    val content: String,
) {
    val fileName: String get() = "$baseName.${format.extension}"
}

/**
 * Where a document ends up on this platform.
 *
 * Implemented per platform rather than through `expect`/`actual` for the same reason
 * `DatabaseDriverFactory` is: an interface can be faked in a test, and the Koin platform
 * module is already the place where platform differences are resolved.
 */
interface DocumentSink {
    suspend fun save(document: Document): SavedDocument

    /**
     * Whether this platform can hand the file to something else.
     *
     * A share sheet on a phone; nothing on a desktop, where saving to Downloads already
     * puts the file where every mail client's picker opens, and nothing in a browser,
     * where the download is the handover.
     */
    val canShare: Boolean get() = false

    /**
     * Saves the document, then offers it to whatever the platform shares with.
     *
     * Saving happens first and unconditionally, and the saved location is returned even
     * when the share sheet fails to appear. That ordering is the point: presenting a sheet
     * touches window hierarchies and content providers, which fail in ways that compile
     * perfectly, and a studio that pressed *Send* must end up with the document either way
     * rather than with an error and nothing on disk.
     */
    suspend fun share(document: Document): SavedDocument = save(document)
}

/**
 * What happened to a saved document.
 *
 * [location] is shown to the person who pressed the button. A document written somewhere
 * they cannot find is a document that was not saved, and saying nothing about where it
 * went is how that happens.
 */
data class SavedDocument(
    val fileName: String,
    val location: String,
)
