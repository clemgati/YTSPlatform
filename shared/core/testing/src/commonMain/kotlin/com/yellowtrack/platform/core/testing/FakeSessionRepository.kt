package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.SessionRepository
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class FakeSessionRepository(
    initial: List<Session> = emptyList(),
) : SessionRepository {
    private val state = MutableStateFlow(initial)

    var failure: Throwable? = null

    override fun observeSessions(): Flow<List<Session>> =
        state.map { sessions ->
            failure?.let { throw it }
                ?: sessions.sortedBy(Session::startsAt)
        }

    override fun observeSession(sessionId: SessionId): Flow<Session?> =
        state.map { sessions ->
            sessions.firstOrNull {
                it.id ==
                    sessionId
            }
        }

    override fun observeSessionsForProject(projectId: ProjectId): Flow<List<Session>> =
        state.map { sessions -> sessions.filter { it.projectId == projectId }.sortedBy(Session::startsAt) }

    override fun observeSessionsBetween(
        fromInclusive: Instant,
        toExclusive: Instant,
    ): Flow<List<Session>> =
        state.map { sessions ->
            failure?.let { throw it }
            sessions
                .filter { it.startsAt >= fromInclusive && it.startsAt < toExclusive }
                .sortedBy(Session::startsAt)
        }

    override fun observeUpcomingSessions(
        from: Instant,
        limit: Int,
    ): Flow<List<Session>> =
        state.map { sessions ->
            failure?.let { throw it }
            sessions.filter { it.startsAt >= from }.sortedBy(Session::startsAt).take(limit)
        }

    override suspend fun getSession(sessionId: SessionId): Session? = state.value.firstOrNull { it.id == sessionId }

    override suspend fun saveSession(session: Session) {
        state.value = state.value.filterNot { it.id == session.id } + session
    }

    override suspend fun deleteSession(sessionId: SessionId) {
        state.value = state.value.filterNot { it.id == sessionId }
    }
}
