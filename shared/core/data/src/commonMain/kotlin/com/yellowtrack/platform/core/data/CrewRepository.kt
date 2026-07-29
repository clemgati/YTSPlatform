package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow

interface CrewRepository {
    /** Everyone working a session, earliest call first. */
    fun observeCrewForSession(sessionId: SessionId): Flow<List<CrewMember>>

    suspend fun getCrewMember(crewMemberId: CrewMemberId): CrewMember?

    suspend fun saveCrewMember(crewMember: CrewMember)

    suspend fun deleteCrewMember(crewMemberId: CrewMemberId)
}
