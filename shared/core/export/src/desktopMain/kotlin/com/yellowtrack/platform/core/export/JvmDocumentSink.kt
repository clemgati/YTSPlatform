package com.yellowtrack.platform.core.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes documents to the desktop's Downloads folder.
 *
 * Downloads rather than a folder of the application's own, because the next thing that
 * happens to this file is that someone attaches it to a message — and the file picker in
 * every mail client opens there.
 */
class JvmDocumentSink(
    private val directory: File = defaultDirectory(),
) : DocumentSink {
    override suspend fun save(document: Document): SavedDocument =
        withContext(Dispatchers.IO) {
            directory.mkdirs()

            // Two call sheets for the same day would otherwise overwrite each other
            // silently, and the one that mattered would be the one that went missing.
            val target = directory.resolve(document.fileName).firstFreeName()
            target.writeText(document.content)

            SavedDocument(fileName = target.name, location = target.absolutePath)
        }

    private companion object {
        fun defaultDirectory(): File {
            val home = File(System.getProperty("user.home") ?: ".")
            val downloads = home.resolve("Downloads")

            return if (downloads.isDirectory) downloads else home
        }

        /** `call-sheet.html`, then `call-sheet-2.html`, and so on. */
        fun File.firstFreeName(): File {
            if (!exists()) return this

            val stem = nameWithoutExtension
            val extension = extension

            return generateSequence(2) { it + 1 }
                .map { parentFile.resolve("$stem-$it.$extension") }
                .first { !it.exists() }
        }
    }
}
