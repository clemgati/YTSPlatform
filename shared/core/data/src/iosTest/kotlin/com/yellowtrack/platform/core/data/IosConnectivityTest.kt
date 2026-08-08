package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.sync.IosConnectivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * `NWPathMonitor`, against whatever network the simulator actually has.
 *
 * Like the desktop probe test, this asserts that it *answers* rather than what it answers —
 * the answer depends on the machine, and a test that expected `true` would fail on an
 * aeroplane and pass everywhere else.
 *
 * What it is really holding is the cinterop wiring, which is where this could silently do
 * nothing. `nw_path_monitor_create`, the update handler, the dispatch queue and
 * `nw_path_monitor_start` all compile whether or not they are hooked up correctly, and a
 * handler that is never called produces a flow that never emits — indistinguishable, from
 * the caller's side, from a device that is simply offline. That is precisely the failure
 * this repository keeps meeting: something that reports nothing looking like something with
 * nothing to report.
 */
class IosConnectivityTest {
    /**
     * `runBlocking` rather than `runTest`, deliberately.
     *
     * The path update arrives on a real dispatch queue in real time. `runTest` advances a
     * virtual clock and would skip straight past the wait without the callback ever having
     * had a chance to fire — turning a genuine five-second window into no window at all.
     *
     * A block body rather than an expression body, here and below. Kotlin/Native requires a
     * test function to return `Unit`, and `= runBlocking { assertNotNull(...) }` returns the
     * asserted value instead — which compiles on every other target and fails only here.
     */
    @Test
    fun `the real monitor reports a path without being asked twice`() {
        runBlocking {
            val reported =
                withTimeoutOrNull(TIMEOUT) {
                    IosConnectivity().online.first()
                }

            assertNotNull(
                reported,
                "NWPathMonitor reports the first path as soon as it starts. Nothing arriving means the " +
                    "handler is not wired to the monitor rather than that the simulator is offline.",
            )
        }
    }

    /** Starting a second monitor must not depend on the first having been cancelled. */
    @Test
    fun `two collectors each get their own monitor`() {
        runBlocking {
            val first = withTimeoutOrNull(TIMEOUT) { IosConnectivity().online.first() }
            val second = withTimeoutOrNull(TIMEOUT) { IosConnectivity().online.first() }

            assertNotNull(first)
            assertNotNull(second, "a monitor created after another has been cancelled must still start")
        }
    }

    private companion object {
        /**
         * Generous on purpose. This is a real callback on a real queue and the cost of being
         * too tight is a test that fails on a loaded machine for no reason — the worst kind,
         * because the reflex is to disable it rather than to read it.
         */
        val TIMEOUT = 5.seconds
    }
}
