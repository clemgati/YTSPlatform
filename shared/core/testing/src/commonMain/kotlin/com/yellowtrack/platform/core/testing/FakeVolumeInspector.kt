package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.common.storage.VolumeContents
import com.yellowtrack.platform.core.common.storage.VolumeInspector

/**
 * A filesystem that only exists in the test.
 *
 * @param contents what each path holds. A path that is absent from the map reads as a
 *   drive that is not plugged in, which is the case worth exercising most.
 */
class FakeVolumeInspector(
    private val contents: Map<String, VolumeContents> = emptyMap(),
    override val isSupported: Boolean = true,
) : VolumeInspector {
    private val reads = mutableListOf<String>()

    /** Which paths were opened, so a test can assert nothing was read that should not be. */
    val pathsRead: List<String> get() = reads.toList()

    override suspend fun inspect(path: String): VolumeContents {
        reads += path

        return contents[path] ?: VolumeContents.Missing
    }

    companion object {
        fun holding(
            path: String,
            files: Int,
            bytes: Long = files * 25_000_000L,
        ) = FakeVolumeInspector(mapOf(path to VolumeContents(exists = true, fileCount = files, totalBytes = bytes)))
    }
}
