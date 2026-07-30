package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.MediaCopyRepository
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMediaCopyRepository(
    initial: List<MediaCopy> = emptyList(),
) : MediaCopyRepository {
    private val state = MutableStateFlow(initial)

    /**
     * Every copy, whatever session it belongs to.
     *
     * Test-only, and the seam `FakeStorageVolumeRepository` reads so that "what is on this
     * drive?" answers from the same store the session page does — two independent stores
     * would let a test pass while the two views of one copy disagreed.
     */
    fun observeAll(): Flow<List<MediaCopy>> = state

    override fun observeCopiesForSession(sessionId: SessionId): Flow<List<MediaCopy>> =
        state.map { copies ->
            copies.filter { it.sessionId == sessionId }.sortedBy { it.audit.createdAt }
        }

    override suspend fun getCopy(copyId: MediaCopyId): MediaCopy? = state.value.firstOrNull { it.id == copyId }

    override suspend fun saveCopy(copy: MediaCopy) {
        state.value = state.value.filterNot { it.id == copy.id } + copy
    }

    override suspend fun deleteCopy(copyId: MediaCopyId) {
        state.value = state.value.filterNot { it.id == copyId }
    }
}
