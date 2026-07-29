package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.ShotRepository
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeShotRepository(
    initial: List<Shot> = emptyList(),
) : ShotRepository {
    private val state = MutableStateFlow(initial)

    var failure: Throwable? = null

    override fun observeShotsForSession(sessionId: SessionId): Flow<List<Shot>> =
        state.map { shots ->
            failure?.let { throw it }

            shots
                .filter { it.sessionId == sessionId }
                .sortedWith(compareBy({ it.position }, { it.audit.createdAt }))
        }

    override suspend fun getShot(shotId: ShotId): Shot? = state.value.firstOrNull { it.id == shotId }

    override suspend fun saveShot(shot: Shot) {
        state.value = state.value.filterNot { it.id == shot.id } + shot
    }

    override suspend fun deleteShot(shotId: ShotId) {
        state.value = state.value.filterNot { it.id == shotId }
    }
}
