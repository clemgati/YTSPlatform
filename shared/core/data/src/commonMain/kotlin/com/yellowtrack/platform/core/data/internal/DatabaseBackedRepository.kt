package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.database.DatabaseProvider
import com.yellowtrack.platform.core.database.YellowTrackDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Shared plumbing for repositories whose database is created lazily.
 *
 * Observation starts with a suspending call to obtain the database, so every `observe`
 * returns a flow that resolves the database when it is first collected rather than when
 * the repository is constructed.
 */
internal abstract class DatabaseBackedRepository(
    private val provider: DatabaseProvider,
) {
    protected suspend fun database(): YellowTrackDatabase = provider.database()

    protected fun <T> observing(query: (YellowTrackDatabase) -> Flow<T>): Flow<T> = flow { emitAll(query(database())) }
}
