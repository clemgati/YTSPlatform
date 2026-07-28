package com.yellowtrack.platform.core.data.internal

import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Observes a query, re-reading whenever the underlying tables change.
 *
 * The generated API is asynchronous — a consequence of supporting the web worker driver —
 * so results are awaited rather than executed synchronously.
 */
internal fun <T : Any> Query<T>.asListFlow(dispatcher: CoroutineDispatcher): Flow<List<T>> =
    asFlow().map { it.awaitAsList() }.flowOn(dispatcher)

internal fun <T : Any> Query<T>.asOneOrNullFlow(dispatcher: CoroutineDispatcher): Flow<T?> =
    asFlow().map { it.awaitAsOneOrNull() }.flowOn(dispatcher)
