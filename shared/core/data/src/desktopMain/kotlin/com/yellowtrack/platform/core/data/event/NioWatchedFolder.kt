package com.yellowtrack.platform.core.data.event

import java.io.File

/**
 * The watched folder, on a real disk.
 *
 * Polling rather than `WatchService`. The file system's own notifications are the tempting
 * choice and the wrong one here: they say a file changed, not that a camera finished with
 * it, so the settling in [FolderWatch] would still be needed — and on macOS the JDK's
 * implementation polls internally anyway, so the notifications would cost a second layer of
 * machinery to arrive later than a direct look. A capture folder has hundreds of files, not
 * millions; listing it costs nothing worth saving.
 *
 * Nothing here decides what to send. This reports what is on disk and [FolderWatch] decides,
 * which is why every interesting property of the watcher is testable without a disk.
 */
class NioWatchedFolder(
    private val directory: File,
) : WatchedFolder {
    override fun list(): List<WatchedFile> {
        // Null when the folder has been unmounted or renamed mid-event — a card reader
        // pulled, a network volume dropped. Empty rather than an exception: the next sweep
        // looks again, and the watcher carries on for whichever other folders are fine.
        val files = directory.listFiles() ?: return emptyList()

        return files
            .filter { it.isFile }
            .map { WatchedFile(path = it.name, sizeBytes = it.length(), modifiedAt = it.lastModified()) }
    }

    /**
     * Paths are file names within the folder, never traversals out of it.
     *
     * They only ever come from [list], so this cannot currently be reached with anything
     * else — but it is the sort of thing that becomes reachable later, and a watcher that
     * can be pointed at `../../.ssh/id_rsa` is a different program from the one intended.
     */
    override fun read(path: String): ByteArray {
        require(!path.contains('/') && !path.contains('\\') && path != "..") {
            "a watched file is a name within the folder, not a path: $path"
        }

        return File(directory, path).readBytes()
    }
}
