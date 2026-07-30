package com.yellowtrack.platform.core.common.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDirectoryEnumerator
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber

/**
 * Walks a folder inside the app's sandbox.
 *
 * iOS will not hand an application an arbitrary path on an external drive without the
 * document picker, so in practice this reads places the app already owns. That is a real
 * limit rather than a gap to be papered over: the studio using this to check a shoot on a
 * Samsung T7 is doing it from the desktop.
 */
class IosVolumeInspector : VolumeInspector {
    override val isSupported: Boolean = true

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun inspect(path: String): VolumeContents {
        val manager = NSFileManager.defaultManager

        if (!manager.fileExistsAtPath(path)) return VolumeContents.Missing

        val enumerator: NSDirectoryEnumerator =
            manager.enumeratorAtPath(path) ?: return singleFile(manager, path)

        var files = 0
        var bytes = 0L

        while (true) {
            val relative = enumerator.nextObject() as? String ?: break
            val full = "$path/$relative"
            val attributes = manager.attributesOfItemAtPath(full, null) ?: continue
            val size = (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: continue

            // Directories report a size too; only entries the enumerator can size as
            // files are counted, which is what the desktop walk counts.
            if (!isDirectory(manager, full)) {
                files++
                bytes += size
            }
        }

        return VolumeContents(exists = true, fileCount = files, totalBytes = bytes)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun singleFile(
        manager: NSFileManager,
        path: String,
    ): VolumeContents {
        val size =
            (manager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

        return VolumeContents(exists = true, fileCount = 1, totalBytes = size)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun isDirectory(
        manager: NSFileManager,
        path: String,
    ): Boolean {
        // The out-parameter form needs cinterop plumbing for one boolean; asking the
        // manager whether it can enumerate the path answers the same question.
        val contents = manager.contentsOfDirectoryAtPath(path, null)

        return contents != null
    }
}
