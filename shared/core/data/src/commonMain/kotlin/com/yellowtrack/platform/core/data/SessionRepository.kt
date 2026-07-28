package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface SessionRepository {
    fun observeSessions(): Flow<List<Session>>

    fun observeSession(sessionId: SessionId): Flow<Session?>

    fun observeSessionsForProject(projectId: ProjectId): Flow<List<Session>>

    /** Sessions starting within a half-open interval, so day queries cannot double-count. */
    fun observeSessionsBetween(
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Flow<List<Session>>

    fun observeUpcomingSessions(
        from: Instant,
        limit: Int,
    ): Flow<List<Session>>

    suspend fun getSession(sessionId: SessionId): Session?

    suspend fun saveSession(session: Session)

    suspend fun deleteSession(sessionId: SessionId)
}
