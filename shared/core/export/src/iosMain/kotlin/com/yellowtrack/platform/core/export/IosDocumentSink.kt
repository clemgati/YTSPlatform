package com.yellowtrack.platform.core.export

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/**
 * Writes documents to the app's Documents directory.
 *
 * Reachable from the Files app when the app declares `UIFileSharingEnabled`, and offered
 * onward through the system share sheet.
 */
class IosDocumentSink : DocumentSink {
    override val canShare: Boolean = true

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun save(document: Document): SavedDocument {
        val directory =
            NSSearchPathForDirectoriesInDomains(
                directory = NSDocumentDirectory,
                domainMask = NSUserDomainMask,
                expandTilde = true,
            ).first() as String

        val path = "$directory/${document.fileName}"

        (document.content as NSString).writeToURL(
            url = NSURL.fileURLWithPath(path),
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )

        return SavedDocument(fileName = document.fileName, location = path)
    }

    /**
     * Writes the file, then presents the system share sheet on top of whatever is showing.
     *
     * The window hierarchy is reached rather than injected, so nothing has to be threaded
     * from the Swift side into dependency injection. Everything after the write is wrapped:
     * a key window that has gone away, or a controller already presenting something, must
     * not cost the studio the document it asked for.
     */
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun share(document: Document): SavedDocument {
        val saved = save(document)

        runCatching {
            val url = NSURL.fileURLWithPath(saved.location)
            val sheet =
                UIActivityViewController(
                    activityItems = listOf(url),
                    applicationActivities = null,
                )

            val root =
                UIApplication.sharedApplication.keyWindow
                    ?.rootViewController

            // Presenting from the topmost controller rather than the root: presenting on a
            // controller that is itself presenting something is silently ignored.
            var presenter = root
            while (presenter?.presentedViewController != null) {
                presenter = presenter.presentedViewController
            }

            presenter?.presentViewController(sheet, animated = true, completion = null)
        }

        return saved
    }
}
