package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.sync.DesktopConnectivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * The desktop answer to "is there a network", which has to be polled because the JVM offers
 * no callback for it.
 *
 * Driven through an injected probe rather than the machine's real interfaces: a test that
 * depends on whether the laptop running it has Wi-Fi is a test that fails on an aeroplane and
 * passes everywhere else, which teaches nobody anything.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopConnectivityTest {
    /** The transition the whole feature exists for: a lid closes, and then it opens. */
    @Test
    fun `reports the connection coming back`() =
        runTest {
            // Holds the last answer once the script runs out, rather than emptying. The poll
            // loop is cancelled by `take`, but not before it has asked once more — so a
            // removeFirst() here threw NoSuchElementException, on CI and eventually here too.
            // It passed on this machine for a while purely on timing, which is the worst way
            // for a test to pass.
            val answers = ArrayDeque(listOf(true, false, false, true))
            val connectivity =
                DesktopConnectivity(interval = 1.milliseconds) {
                    if (answers.size > 1) answers.removeFirst() else answers.first()
                }

            val seen = connectivity.online.take(3).toList()

            assertEquals(listOf(true, false, true), seen, "up, gone, and back")
        }

    /**
     * Only changes leave the flow. A poll every few seconds that emitted every time would
     * make [com.yellowtrack.platform.core.data.sync.Synchroniser] reconcile every few seconds
     * — a second timer, and a much worse one than the timer it was meant to help.
     */
    @Test
    fun `a connection that stays up is reported once`() =
        runTest {
            val connectivity = DesktopConnectivity(interval = 1.milliseconds) { true }

            val seen = connectivity.online.take(1).toList()

            assertEquals(listOf(true), seen)
        }

    /**
     * The real probe, against whatever this machine actually has.
     *
     * Asserts only that it answers rather than what it answers, because the answer depends on
     * the machine. It is here because the interesting failure is an exception out of
     * `NetworkInterface` on some platform, and that would be invisible to every test above.
     */
    @Test
    fun `the real probe answers without throwing`() =
        runTest {
            val connectivity = DesktopConnectivity(interval = 1.milliseconds)

            val seen = connectivity.online.take(1).toList()

            assertTrue(seen.size == 1, "it should reach a conclusion about this machine, either way")
        }
}
