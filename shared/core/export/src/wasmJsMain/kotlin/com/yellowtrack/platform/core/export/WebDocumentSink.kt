package com.yellowtrack.platform.core.export

import org.w3c.dom.HTMLAnchorElement
import kotlinx.browser.document as browserDocument

/**
 * Hands the document to the browser as a download.
 *
 * A data URL rather than a Blob because it needs no object-URL lifetime to manage and no
 * revoke to forget; a call sheet is a few kilobytes of text, far inside the size where
 * that trade would matter.
 *
 * The browser decides where the file lands and does not tell the page, so the location is
 * reported as the browser's own — saying "Downloads" would be a guess, and a guess about
 * where someone's file went is the kind that wastes ten minutes.
 */
class WebDocumentSink : DocumentSink {
    override suspend fun save(document: Document): SavedDocument {
        val anchor = browserDocument.createElement("a") as HTMLAnchorElement
        anchor.href =
            "data:${document.format.mediaType};charset=utf-8,${encodeUriComponent(document.content)}"
        anchor.download = document.fileName

        // Must be in the document for the click to be honoured in Firefox.
        browserDocument.body?.appendChild(anchor)
        anchor.click()
        browserDocument.body?.removeChild(anchor)

        return SavedDocument(fileName = document.fileName, location = "your browser's downloads")
    }
}

private fun encodeUriComponent(value: String): String = js("encodeURIComponent(value)")
