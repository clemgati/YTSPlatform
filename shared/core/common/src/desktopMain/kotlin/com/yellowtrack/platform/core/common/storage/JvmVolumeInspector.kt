package com.yellowtrack.platform.core.common.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Walks a folder on a machine that has a filesystem.
 *
 * Shared by desktop and Android: both are JVM, and `java.io.File` reads an external drive
 * on one and the app's own storage on the other without either needing its own walk.
 */
class JvmVolumeInspector : VolumeInspector {
    override val isSupported: Boolean = true

    override suspend fun inspect(path: String): VolumeContents =
        withContext(Dispatchers.IO) {
            val root = File(path)
            if (!root.exists()) return@withContext VolumeContents.Missing

            if (root.isFile) {
                return@withContext VolumeContents(exists = true, fileCount = 1, totalBytes = root.length())
            }

            var files = 0
            var bytes = 0L

            // walkTopDown rather than listFiles: a shoot is filed in folders — by card, by
            // camera, by day — and counting only the top level would report a wedding as
            // eight items.
            root.walkTopDown().forEach { entry ->
                if (entry.isFile) {
                    files++
                    bytes += entry.length()
                }
            }

            VolumeContents(exists = true, fileCount = files, totalBytes = bytes)
        }
}
