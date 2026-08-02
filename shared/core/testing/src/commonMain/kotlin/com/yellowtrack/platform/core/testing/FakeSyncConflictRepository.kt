package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.data.SyncConflictRepository
import com.yellowtrack.platform.core.model.sync.SyncConflict
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeSyncConflictRepository(
    initial: List<SyncConflict> = emptyList(),
) : SyncConflictRepository {
    private val conflicts = MutableStateFlow(initial)

    /** Ids passed to [resolve], so a test can check the dismissal reached the repository. */
    val resolved = mutableListOf<SyncConflictId>()

    override fun observeUnresolved(): Flow<List<SyncConflict>> =
        conflicts.map { it.filterNot(SyncConflict::isResolved) }

    override fun observeUnresolvedCount(): Flow<Int> = observeUnresolved().map { it.size }

    override suspend fun resolve(id: SyncConflictId) {
        resolved += id
        conflicts.value = conflicts.value.filterNot { it.id == id }
    }

    fun emit(next: List<SyncConflict>) {
        conflicts.value = next
    }
}
