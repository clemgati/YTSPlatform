package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import kotlinx.coroutines.flow.Flow

/** The studio's drives, and what is on each of them. */
interface StorageVolumeRepository {
    fun observeVolumes(): Flow<List<StorageVolume>>

    suspend fun getVolume(volumeId: StorageVolumeId): StorageVolume?

    suspend fun saveVolume(volume: StorageVolume)

    suspend fun deleteVolume(volumeId: StorageVolumeId)

    /**
     * Every copy sitting on one drive.
     *
     * The question the register exists to answer: a drive has died, and the studio needs
     * to know which shoots were on it before it can decide what to do about any of them.
     */
    fun observeCopiesOnVolume(volumeId: StorageVolumeId): Flow<List<MediaCopy>>
}
