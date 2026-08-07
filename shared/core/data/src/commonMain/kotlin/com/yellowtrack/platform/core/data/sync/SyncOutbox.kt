package com.yellowtrack.platform.core.data.sync

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.database.DatabaseProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * How much this device is still holding, as it changes.
 *
 * Its own small thing rather than a method on the engine, because the only caller is the
 * scheduler and the scheduler deliberately knows nothing about how reconciliation works.
 *
 * Work sitting in the outbox is work only this device has. Until it is pushed, a lost phone
 * is lost bookings — so the number going up is the strongest reason to reconcile there is,
 * and it was the one signal nothing was watching.
 */
class SyncOutbox(
    private val provider: DatabaseProvider,
    private val studioContext: StudioContext,
    private val dispatcher: CoroutineDispatcher,
) {
    fun pending(): Flow<Long> =
        flow { emit(provider.database()) }
            .flatMapLatest { database ->
                database.outboxQueries
                    .countPending(studioContext.studioId.value)
                    .asFlow()
                    .mapToOne(dispatcher)
            }
}
