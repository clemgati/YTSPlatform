package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import kotlinx.coroutines.flow.Flow

interface ShotRepository {
    /** Everything promised for a session, in the order it should be worked. */
    fun observeShotsForSession(sessionId: SessionId): Flow<List<Shot>>

    suspend fun getShot(shotId: ShotId): Shot?

    suspend fun saveShot(shot: Shot)

    suspend fun deleteShot(shotId: ShotId)
}
