package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.auth.AuthApi
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.data.auth.SessionStore
import com.yellowtrack.platform.core.data.auth.StoredSession
import com.yellowtrack.platform.core.data.sync.SyncReport
import com.yellowtrack.platform.core.data.sync.SyncStatus
import com.yellowtrack.platform.core.data.sync.SyncUnauthorised
import com.yellowtrack.platform.core.data.sync.Synchroniser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    private fun world(reconcile: suspend () -> SyncReport): World {
        val auth = AuthRepository(store = StoredSessionStore(), api = UnusedApi)
        return World(
            Synchroniser(
                reconcile = reconcile,
                auth = auth,
                scope = CoroutineScope(UnconfinedTestDispatcher()),
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

        override suspend fun requestPasswordReset(email: String) = error("unused")

        override suspend fun resetPassword(
            email: String,
            code: String,
            newPassword: String,
        ) = error("unused")
    }
}
