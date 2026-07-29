package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.TalentReleaseRepository
import com.yellowtrack.platform.core.model.release.ReleaseStatus
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTalentReleaseRepository(
    initial: List<TalentRelease> = emptyList(),
) : TalentReleaseRepository {
    private val state = MutableStateFlow(initial)

    override fun observeReleasesForSession(sessionId: SessionId): Flow<List<TalentRelease>> =
        state.map { releases ->
            releases
                .filter { it.sessionId == sessionId }
                // Matches the query: signed ones sort last, since they need no chasing.
                .sortedWith(compareBy({ it.status == ReleaseStatus.Signed }, { it.personName }))
        }

    override suspend fun getRelease(releaseId: TalentReleaseId): TalentRelease? =
        state.value.firstOrNull { it.id == releaseId }

    override suspend fun saveRelease(release: TalentRelease) {
        state.value = state.value.filterNot { it.id == release.id } + release
    }

    override suspend fun deleteRelease(releaseId: TalentReleaseId) {
        state.value = state.value.filterNot { it.id == releaseId }
    }
}
