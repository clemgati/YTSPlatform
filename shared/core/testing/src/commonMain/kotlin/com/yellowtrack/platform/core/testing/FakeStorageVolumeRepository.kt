package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.StorageVolumeRepository
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * @param copies where the copies live, so "what is on this drive?" answers from the same
 *   place the session page reads. Two independent stores would let a test pass while the
 *   two views of one copy disagreed.
 */
class FakeStorageVolumeRepository(
    initial: List<StorageVolume> = emptyList(),
    private val copies: FakeMediaCopyRepository = FakeMediaCopyRepository(),
) : StorageVolumeRepository {
    private val state = MutableStateFlow(initial)

    override fun observeVolumes(): Flow<List<StorageVolume>> = state.map { volumes -> volumes.sortedBy { it.label } }

    override suspend fun getVolume(volumeId: StorageVolumeId): StorageVolume? =
        state.value.firstOrNull { it.id == volumeId }

    override suspend fun saveVolume(volume: StorageVolume) {
        state.value = state.value.filterNot { it.id == volume.id } + volume
    }

    override suspend fun deleteVolume(volumeId: StorageVolumeId) {
        state.value = state.value.filterNot { it.id == volumeId }
    }

    override fun observeCopiesOnVolume(volumeId: StorageVolumeId): Flow<List<MediaCopy>> =
        copies.observeAll().map { all -> all.filter { it.volumeId == volumeId } }
}
