package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow

interface MediaCopyRepository {
    /** Every recorded copy of a shoot's files. */
    fun observeCopiesForSession(sessionId: SessionId): Flow<List<MediaCopy>>

    suspend fun getCopy(copyId: MediaCopyId): MediaCopy?

    suspend fun saveCopy(copy: MediaCopy)

    suspend fun deleteCopy(copyId: MediaCopyId)
}
