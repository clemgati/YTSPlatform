package com.yellowtrack.platform.core.export

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToURL

/**
 * Writes documents to the app's Documents directory.
 *
 * Reachable from the Files app when the app declares `UIFileSharingEnabled`, and reachable
 * from a share sheet once there is a `UIViewController` to present one from. As on
 * Android, presenting that sheet is the next step and is not pretended at here.
 */
class IosDocumentSink : DocumentSink {
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
}
