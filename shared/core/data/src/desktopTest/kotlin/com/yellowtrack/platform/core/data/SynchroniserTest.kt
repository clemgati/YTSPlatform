package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.auth.AuthApi
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.data.auth.SessionStore
import com.yellowtrack.platform.core.data.auth.StoredSession
import com.yellowtrack.platform.core.data.sync.AppVisibility
import com.yellowtrack.platform.core.data.sync.Connectivity
import com.yellowtrack.platform.core.data.sync.SyncReport
import com.yellowtrack.platform.core.data.sync.SyncStatus
import com.yellowtrack.platform.core.data.sync.SyncUnauthorised
import com.yellowtrack.platform.core.data.sync.Synchroniser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * When to reconcile, and what to do when the answer is "you are not who you were".
 *
 * The sign-out case had been written, committed, and was not actually there: a patch failed
 * to apply against reformatted code and nothing noticed, because nothing tested it. Running
 * the application found it. This is what stops that happening twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SynchroniserTest {
    @Test
    fun `a revoked session signs the device out rather than failing forever`() =
        runTest {
            val world = world { throw SyncUnauthorised() }
            world.auth.restore(now = 0)
            assertTrue(world.auth.session.value is SessionState.SignedIn)

            world.synchroniser.syncNow()

            assertTrue(
                world.auth.session.value is SessionState.SignedOut,
                "the token is genuinely gone, so the studio needs a sign-in rather than an error " +
                    "whose remedy nobody offered",
            )
            assertEquals(
                SyncStatus.Idle,
                world.synchroniser.status.value,
                "and no failure is left on the screen, because being signed out is not a fault",
            )
        }

    @Test
    fun `an ordinary failure is reported and leaves the session alone`() =
        runTest {
            val world = world { error("the venue has no signal") }
            world.auth.restore(now = 0)

            world.synchroniser.syncNow()

            assertTrue(
                world.auth.session.value is SessionState.SignedIn,
                "a device with no signal is still signed in, and signing it out would lose the " +
                    "session it will need when the signal comes back",
            )
            assertTrue(world.synchroniser.status.value is SyncStatus.Failed)
        }

    @Test
    fun `nothing runs while signed out`() =
        runTest {
            var attempts = 0
            val world =
                world {
                    attempts++
                    SyncReport(0, 0, 0, 0, 0)
                }

            world.synchroniser.syncNow()

            assertEquals(0, attempts, "a device with nowhere to sync to should not spend a round trip finding out")
        }

    @Test
    fun `a successful sync reports what it did`() =
        runTest {
            val world = world { SyncReport(uploaded = 2, downloaded = 3, conflicted = 1, rejected = 0, cursor = 9) }
            world.auth.restore(now = 0)

            world.synchroniser.syncNow()

            val status = world.synchroniser.status.value
            assertTrue(status is SyncStatus.Succeeded)
            assertEquals(2, status.report.uploaded)
        }

    private class World(
        val synchroniser: Synchroniser,
        val auth: AuthRepository,
    )

    // -- Reconciling when there is something to reconcile ------------------------------------

    /**
     * The trigger a timer cannot be. Work that has not left the device is work only that
     * device has, and waiting out an interval chosen for idleness is the wrong answer to
     * somebody having just typed something.
     */
    @Test
    fun `a write brings the next run forward`() =
        runTest {
            var runs = 0
            val pending = MutableStateFlow(0L)
            val world =
                world(pendingWork = pending, scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
                    runs++
                    SyncReport(0, 0, 0, 0, 0)
                }
            world.auth.restore(now = 0)
            world.synchroniser.startSyncOnWrite()

            pending.value = 1
            advanceTimeBy(Synchroniser.AFTER_WRITE_DELAY + 1.seconds)

            assertEquals(1, runs, "a row queued for upload should not wait out the interval")
        }

    /** Saving a form writes several rows, and one reconciliation covers all of them. */
    @Test
    fun `several writes in a row produce one reconciliation`() =
        runTest {
            var runs = 0
            val pending = MutableStateFlow(0L)
            val world =
                world(pendingWork = pending, scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))) {
                    runs++
                    SyncReport(0, 0, 0, 0, 0)
                }
            world.auth.restore(now = 0)
            world.synchroniser.startSyncOnWrite()

            pending.value = 1
            pending.value = 2
            pending.value = 3
            advanceTimeBy(Synchroniser.AFTER_WRITE_DELAY + 1.seconds)

            assertEquals(1, runs, "a studio correcting a typo three times should upload once")
        }

    // -- Coming back -----------------------------------------------------------------------

    /**
     * The moment this exists for.
     *
     * The backoff in `backoffFrom` is right for a device at a venue with no signal: asking
     * every five minutes all day costs battery to learn something already known. It is wrong
     * the instant the signal returns, because that is when the answer changed — and a device
     * that had failed enough to reach the hour-long ceiling would otherwise sit there with a
     * working connection.
     */
    @Test
    fun `a connection coming back reconciles without waiting for the timer`() =
        runTest {
            var runs = 0
            val online = MutableStateFlow(false)
            val world =
                world(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    connectivity = ReportedConnectivity(online),
                ) {
                    runs++
                    SyncReport(0, 0, 0, 0, 0)
                }
            world.auth.restore(now = 0)
            world.synchroniser.startSyncOnReconnect()

            online.value = true

            assertEquals(1, runs, "the connection came back and nothing noticed, which is the fault")
        }

    /** A connection that stays up is not news. Only the transition is. */
    @Test
    fun `staying online does not keep triggering runs`() =
        runTest {
            var runs = 0
            val online = MutableStateFlow(false)
            val world =
                world(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    connectivity = ReportedConnectivity(online),
                ) {
                    runs++
                    SyncReport(0, 0, 0, 0, 0)
                }
            world.auth.restore(now = 0)
            world.synchroniser.startSyncOnReconnect()

            online.value = true
            online.value = true
            online.value = true

            assertEquals(1, runs, "a flaky connection announcing itself must not become a second timer")
        }

    /** Losing a connection is not a reason to do anything. Regaining it is. */
    @Test
    fun `going offline reconciles nothing`() =
        runTest {
            var runs = 0
            val online = MutableStateFlow(true)
            val world =
                world(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    connectivity = ReportedConnectivity(online),
                ) {
                    runs++
                    SyncReport(0, 0, 0, 0, 0)
                }
            world.auth.restore(now = 0)
            world.synchroniser.startSyncOnReconnect()
            val afterSubscribing = runs

            online.value = false

            assertEquals(afterSubscribing, runs, "there is nowhere to sync to, so asking wastes a request")
        }

    private class ReportedConnectivity(
        override val online: MutableStateFlow<Boolean>,
    ) : Connectivity

    // -- Coming back to the application ------------------------------------------------------

    /**
     * The gap a studio actually notices. A phone spends the afternoon in a pocket, the timer
     * has backed off to an hour, and the screen it is opened to shows what the last run left.
     */
    @Test
    fun `opening the application reconciles without waiting for the timer`() =
        runTest {
            var runs = 0
            val foreground = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
            val world =
                world(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    visibility = ReportedVisibility(foreground),
                ) {
                    runs++
                    SyncReport(0, 0, 0, 0, 0)
                }
            world.auth.restore(now = 0)
            world.synchroniser.startSyncOnForeground()

            foreground.emit(Unit)

            assertEquals(1, runs, "the application was opened and nothing noticed, which is the fault")
        }

    /**
     * Unlike a connection, every foreground is news: the device may have been away for an hour
     * between two of them. This is the property that distinguishes it from
     * [startSyncOnReconnect], which deliberately ignores repeats.
     */
    @Test
    fun `every return to the application reconciles`() =
        runTest {
            var runs = 0
            val foreground = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
            val world =
                world(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    visibility = ReportedVisibility(foreground),
                ) {
                    runs++
                    SyncReport(0, 0, 0, 0, 0)
                }
            world.auth.restore(now = 0)
            world.synchroniser.startSyncOnForeground()

            foreground.emit(Unit)
            foreground.emit(Unit)
            foreground.emit(Unit)

            assertEquals(3, runs, "each return may follow an hour away, so none of them is a repeat")
        }

    /** Signed out, there is nowhere to sync to, and opening the application changes that not at all. */
    @Test
    fun `opening the application while signed out reconciles nothing`() =
        runTest {
            var runs = 0
            val foreground = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
            val world =
                world(
                    scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                    visibility = ReportedVisibility(foreground),
                ) {
                    runs++
                    SyncReport(0, 0, 0, 0, 0)
                }
            world.synchroniser.startSyncOnForeground()

            foreground.emit(Unit)

            assertEquals(0, runs, "a device with no session would only discover it has nowhere to go")
        }

    private class ReportedVisibility(
        override val foregrounded: MutableSharedFlow<Unit>,
    ) : AppVisibility

    // -- Leaving a trace ---------------------------------------------------------------------

    /**
     * A failed sync leaves a stack trace behind, and not only a sentence.
     *
     * The sentence always existed: `SyncStatus.Failed` carries `error.message` and Settings
     * shows it. What did not exist was anywhere the *exception* went — and a message is
     * precisely the part of an exception that omits where it came from.
     *
     * The cost was measured. A device stopped advancing its sync cursor and did so for
     * forty-one hours before anybody noticed, and the diagnosis came entirely from comparing
     * a local cursor against `server_seq` in the database. Nothing was written down anywhere,
     * because the desktop build had no SLF4J provider and this application logged nothing of
     * its own.
     */
    @Test
    fun `a failed sync writes the throwable where somebody can read it`() =
        runTest {
            val world = world { error("the cursor did not advance") }
            world.auth.restore(now = 0)

            val recorded = captureStandardError { world.synchroniser.syncNow() }

            assertTrue(recorded.contains("sync"), "it should say where, in words worth searching for")
            assertTrue(
                recorded.contains("the cursor did not advance"),
                "and it should carry what actually went wrong",
            )
            assertTrue(recorded.contains("at "), "with a stack trace, which is the half a message throws away")
        }

    /** A working sync is not an event. A log that reports success is one nobody reads. */
    @Test
    fun `a sync that worked writes nothing`() =
        runTest {
            val world = world { SyncReport(0, 0, 0, 0, 0) }
            world.auth.restore(now = 0)

            val recorded = captureStandardError { world.synchroniser.syncNow() }

            assertTrue(recorded.isBlank(), "nothing went wrong, so there is nothing to say")
        }

    /**
     * Restored in a `finally`: leaving it swapped would silently swallow every other test's
     * output in the same JVM, which is the sort of fault this pair of tests is about.
     */
    private inline fun captureStandardError(block: () -> Unit): String {
        val original = System.err
        val buffer = java.io.ByteArrayOutputStream()

        return try {
            System.setErr(java.io.PrintStream(buffer, true))
            block()
            buffer.toString()
        } finally {
            System.setErr(original)
        }
    }

    private fun world(
        pendingWork: Flow<Long> = emptyFlow(),
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
        connectivity: Connectivity = Connectivity.Unknown,
        visibility: AppVisibility = AppVisibility.Unknown,
        reconcile: suspend () -> SyncReport,
    ): World {
        val auth = AuthRepository(store = StoredSessionStore(), api = UnusedApi)
        return World(
            Synchroniser(
                reconcile = reconcile,
                auth = auth,
                scope = scope,
                pendingWork = pendingWork,
                connectivity = connectivity,
                visibility = visibility,
            ),
            auth,
        )
    }

    /** Starts out holding a session, so `restore` finds one. */
    private class StoredSessionStore : SessionStore {
        override val isHardwareBacked = false
        private var session: StoredSession? =
            StoredSession(
                token = "a-token",
                expiresAt = Long.MAX_VALUE,
                accountId = "account-1",
                email = "ada@harbourline.test",
                name = "Ada Okafor",
                studioId = "studio-1",
                studioName = "Harbourline Photography",
            )

        override suspend fun read(): StoredSession? = session

        override suspend fun write(session: StoredSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }

    private object UnusedApi : AuthApi {
        override suspend fun signIn(
            email: String,
            password: String,
        ): StoredSession = error("unused")

        override suspend fun signUp(
            email: String,
            password: String,
            name: String,
            studioName: String,
        ): StoredSession = error("unused")

        override suspend fun signOut(token: String) = Unit

        override suspend fun restoreAccount(
            email: String,
            password: String,
        ): StoredSession = error("unused")

        override suspend fun exportStudio(token: String): String = error("unused")

        override suspend fun deleteAccount(
            token: String,
            password: String,
        ): Long = error("unused")

        override suspend fun requestPasswordReset(email: String) = error("unused")

        override suspend fun resetPassword(
            email: String,
            code: String,
            newPassword: String,
        ) = error("unused")
    }
}
