package com.yellowtrack.platform.core.common.storage

/**
 * What was found at a place on disk.
 *
 * Deliberately a count and a size rather than a list of names. A studio checking a backup
 * wants to know whether the wedding is there and roughly how big it is; enumerating four
 * thousand file names would be slower to gather and no more convincing.
 */
data class VolumeContents(
    val exists: Boolean,
    val fileCount: Int,
    val totalBytes: Long,
) {
    /** An empty folder is not a backup, and is the failure a studio most needs told. */
    val holdsFiles: Boolean get() = exists && fileCount > 0

    companion object {
        val Missing = VolumeContents(exists = false, fileCount = 0, totalBytes = 0L)
    }
}

/**
 * Reads a drive to see whether the files are actually there.
 *
 * Until this existed, "verified" meant a studio had pressed a button. A drive can fail
 * silently and a folder can be moved, so a tick recorded without reading anything is a
 * backup nobody has checked wearing the label of one that has been.
 *
 * Implemented per platform in the same shape as `DatabaseDriverFactory` and
 * `DocumentSink`. [isSupported] exists because the web build genuinely has no filesystem
 * to read: the honest answer there is that this device cannot check, not a false negative
 * that would tell a studio its backups had vanished.
 */
interface VolumeInspector {
    val isSupported: Boolean

    /**
     * Reads [path], following folders.
     *
     * Returns [VolumeContents.Missing] when the path does not exist, which covers the
     * case that matters most: an external drive that is not plugged in.
     */
    suspend fun inspect(path: String): VolumeContents
}

/** For the web, and anywhere else with nothing to read. */
class UnsupportedVolumeInspector : VolumeInspector {
    override val isSupported: Boolean = false

    override suspend fun inspect(path: String): VolumeContents = VolumeContents.Missing
}
