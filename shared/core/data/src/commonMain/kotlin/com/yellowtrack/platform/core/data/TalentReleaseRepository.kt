package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow

interface TalentReleaseRepository {
    /** Everyone photographed on a session, those still to sign first. */
    fun observeReleasesForSession(sessionId: SessionId): Flow<List<TalentRelease>>

    suspend fun getRelease(releaseId: TalentReleaseId): TalentRelease?

    suspend fun saveRelease(release: TalentRelease)

    suspend fun deleteRelease(releaseId: TalentReleaseId)
}
