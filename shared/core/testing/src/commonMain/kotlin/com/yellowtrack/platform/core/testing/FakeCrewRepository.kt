package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.CrewRepository
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeCrewRepository(
    initial: List<CrewMember> = emptyList(),
) : CrewRepository {
    private val state = MutableStateFlow(initial)

    override fun observeCrewForSession(sessionId: SessionId): Flow<List<CrewMember>> =
        state.map { crew ->
            crew
                .filter { it.sessionId == sessionId }
                // Matches the query: a missing call time is "whenever", so it sorts last.
                .sortedWith(compareBy({ it.callTime == null }, { it.callTime }, { it.audit.createdAt }))
        }

    override suspend fun getCrewMember(crewMemberId: CrewMemberId): CrewMember? =
        state.value.firstOrNull { it.id == crewMemberId }

    override suspend fun saveCrewMember(crewMember: CrewMember) {
        state.value = state.value.filterNot { it.id == crewMember.id } + crewMember
    }

    override suspend fun deleteCrewMember(crewMemberId: CrewMemberId) {
        state.value = state.value.filterNot { it.id == crewMemberId }
    }
}
