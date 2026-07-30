package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.export.Document
import com.yellowtrack.platform.core.export.DocumentSink
import com.yellowtrack.platform.core.export.SavedDocument

/**
 * Keeps every document instead of writing one.
 *
 * A test has no business touching a filesystem, and what is worth asserting is the
 * document itself — its name, its format, and what is and is not in it.
 */
class RecordingDocumentSink(
    override val canShare: Boolean = false,
) : DocumentSink {
    private val written = mutableListOf<Document>()

    val documents: List<Document> get() = written.toList()

    val last: Document? get() = written.lastOrNull()

    override suspend fun save(document: Document): SavedDocument {
        written += document

        return SavedDocument(fileName = document.fileName, location = "recorded/${document.fileName}")
    }
}
