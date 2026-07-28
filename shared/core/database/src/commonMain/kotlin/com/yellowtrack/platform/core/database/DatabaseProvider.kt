package com.yellowtrack.platform.core.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Creates the database once, on first use, and hands out the same instance thereafter.
 *
 * Driver creation suspends — the web worker handshake is asynchronous — while Koin's
 * `single { }` blocks are not. Blocking during startup is not an option either, because
 * wasm is single-threaded and `runBlocking` does not exist there. So construction is
 * deferred to the first suspending call rather than performed during injection.
 */
class DatabaseProvider(
    private val driverFactory: DatabaseDriverFactory,
) {
    private val mutex = Mutex()
    private var instance: YellowTrackDatabase? = null

    /**
     * Always takes the lock rather than double-checking an unsynchronised field first:
     * `@Volatile` does not exist on wasm, and an uncontended coroutine mutex is cheap
     * enough that the fast path is not worth an unsafe read.
     */
    suspend fun database(): YellowTrackDatabase =
        mutex.withLock {
            instance ?: createYellowTrackDatabase(driverFactory).also { instance = it }
        }
}
