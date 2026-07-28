package com.yellowtrack.platform.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The dispatcher for blocking work — database reads and writes, and later network calls.
 *
 * `Dispatchers.IO` only exists on JVM-backed targets. Native and wasm fall back to
 * `Dispatchers.Default`, which is correct there: neither has a thread pool sized for
 * blocking calls, and wasm is single-threaded regardless.
 */
expect val ioDispatcher: CoroutineDispatcher
