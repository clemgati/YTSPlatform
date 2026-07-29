package com.yellowtrack.platform.core.export

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes documents to the application's own documents folder on external storage.
 *
 * That folder needs no permission and is visible in the Files app, which is the most a
 * document can be without an `Activity` to hand it to a share sheet. Handing it to one is
 * the obvious next step and is deliberately not faked here: a button that claims to have
 * shared a file it only wrote is worse than a button that says where the file is.
 */
class AndroidDocumentSink(
    private val context: Context,
) : DocumentSink {
    override suspend fun save(document: Document): SavedDocument =
        withContext(Dispatchers.IO) {
            val directory =
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir

            directory.mkdirs()
            val target = File(directory, document.fileName)
            target.writeText(document.content)

            SavedDocument(fileName = target.name, location = target.absolutePath)
        }
}
