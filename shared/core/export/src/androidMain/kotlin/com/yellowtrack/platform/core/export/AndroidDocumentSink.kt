package com.yellowtrack.platform.core.export

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes documents to the application's own documents folder, and offers them onward.
 *
 * The folder needs no permission and is visible in the Files app. Sharing goes through a
 * `FileProvider` because handing another application a `file://` URI has thrown
 * `FileUriExposedException` since Android 7.
 */
class AndroidDocumentSink(
    private val context: Context,
) : DocumentSink {
    override val canShare: Boolean = true

    override suspend fun save(document: Document): SavedDocument =
        withContext(Dispatchers.IO) {
            val target = File(directory(), document.fileName)
            target.writeText(document.content)

            SavedDocument(fileName = target.name, location = target.absolutePath)
        }

    override suspend fun share(document: Document): SavedDocument {
        val saved = save(document)

        // The file is already on disk. Everything below can fail — a missing provider
        // declaration, a locked-down launcher — and the studio still has its document.
        runCatching {
            val file = File(directory(), document.fileName)
            val uri =
                FileProvider.getUriForFile(
                    context,
                    // Derived rather than repeated: at runtime this is the applicationId,
                    // which is what the manifest's ${applicationId} resolves to, so the
                    // two cannot drift apart.
                    "${context.packageName}.documents",
                    file,
                )

            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = document.format.mediaType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, document.baseName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            // Started from the application context rather than an Activity, so nothing has
            // to be threaded through dependency injection to reach this point.
            context.startActivity(
                Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        return saved
    }

    private fun directory(): File =
        (context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir)
            .also { it.mkdirs() }
}
