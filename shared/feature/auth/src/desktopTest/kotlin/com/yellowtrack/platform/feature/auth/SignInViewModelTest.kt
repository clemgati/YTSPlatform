package com.yellowtrack.platform.feature.auth

import com.yellowtrack.platform.core.data.auth.AuthApi
import com.yellowtrack.platform.core.data.auth.AuthFailure
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.data.auth.SessionStore
import com.yellowtrack.platform.core.data.auth.StoredSession
import com.yellowtrack.platform.feature.auth.presentation.SignInMode
import com.yellowtrack.platform.feature.auth.presentation.SignInViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signing in stores the session and the shell can see it`() =
        runTest {
            val world = world()
            world.viewModel.fill(email = "ada@harbourline.test", password = "a long enough password")

            world.viewModel.submit()

            assertTrue(world.auth.session.value is SessionState.SignedIn)
            assertEquals(
                session,
                world.store.written,
                "the token has to reach the store, or the next launch signs in again",
            )
        }

    @Test
    fun `a wrong password is reported without guessing which half was wrong`() =
        runTest {
            val world = world(api = FailingApi(AuthFailure.BadCredentials))
            world.viewModel.fill(email = "ada@harbourline.test", password = "wrong")

            world.viewModel.submit()

            assertEquals("That email or password is not right.", world.viewModel.uiState.value.error)
            assertTrue(
                world.auth.session.value is SessionState.SignedOut || world.auth.session.value == SessionState.Unknown,
            )
        }

    @Test
    fun `no signal reads as no signal, not as a bad password`() =
        runTest {
            val world = world(api = FailingApi(AuthFailure.Unreachable))
            world.viewModel.fill(email = "ada@harbourline.test", password = "a long enough password")

            world.viewModel.submit()

            assertEquals(
                "Could not reach the server. Check your connection.",
                world.viewModel.uiState.value.error,
                "telling a photographer at a venue with no signal that their password is wrong " +
                    "sends them round a reset that was never going to help",
            )
        }

    @Test
    fun `the form cannot be submitted until it could possibly succeed`() =
        runTest {
            val world = world()

            assertFalse(world.viewModel.uiState.value.canSubmit, "an empty form has nothing to send")

            world.viewModel.fill(email = "ada@harbourline.test", password = "a long enough password")
            assertTrue(world.viewModel.uiState.value.canSubmit)

            world.viewModel.onModeChanged(SignInMode.SignUp)
            assertFalse(
                world.viewModel.uiState.value.canSubmit,
                "starting a studio needs a name for it, and the server would refuse without one",
            )
        }

    @Test
    fun `switching between signing in and signing up keeps what was typed`() =
        runTest {
            val world = world()
            world.viewModel.onEmailChanged("ada@harbourline.test")

            world.viewModel.onModeChanged(SignInMode.SignUp)

            assertEquals(
                "ada@harbourline.test",
                world.viewModel.uiState.value.fields.email,
                "the address is the same either way, and clearing it punishes guessing wrong",
            )
        }

    @Test
    fun `an error clears as soon as something is retyped`() =
        runTest {
            val world = world(api = FailingApi(AuthFailure.BadCredentials))
            world.viewModel.fill(email = "ada@harbourline.test", password = "wrong")
            world.viewModel.submit()

            world.viewModel.onPasswordChanged("trying again")

            assertNull(
                world.viewModel.uiState.value.error,
                "a stale error next to a corrected field reads as still wrong",
            )
        }

    @Test
    fun `a device that cannot store the session securely says so`() =
        runTest {
            val world = world(hardwareBacked = false)

            assertFalse(
                world.viewModel.uiState.value.isHardwareBacked,
                "a browser cannot protect this, and a studio on a shared machine should be told " +
                    "before signing in rather than after",
            )
        }

    // -- Getting back in ----------------------------------------------------------------

    @Test
    fun `asking for a code moves on to entering one, and says so without promising`() =
        runTest {
            val world = world()
            world.viewModel.onModeChanged(SignInMode.ForgotPassword)
            world.viewModel.onEmailChanged("ada@harbourline.test")

            world.viewModel.submit()

            assertEquals(SignInMode.EnterCode, world.viewModel.uiState.value.mode)
            assertTrue(
                world.viewModel.uiState.value.notice!!
                    .startsWith("If that address has an account"),
                "the server will not say whether the address had an account, so neither can this",
            )
        }

    @Test
    fun `asking for a code needs only an address`() =
        runTest {
            val world = world()
            world.viewModel.onModeChanged(SignInMode.ForgotPassword)

            assertFalse(world.viewModel.uiState.value.canSubmit)
            world.viewModel.onEmailChanged("ada@harbourline.test")
            assertTrue(
                world.viewModel.uiState.value.canSubmit,
                "a password is exactly what the person does not have",
            )
        }

    @Test
    fun `a completed reset returns to signing in and says the devices are out`() =
        runTest {
            val world = world()
            world.viewModel.onModeChanged(SignInMode.EnterCode)
            world.viewModel.onEmailChanged("ada@harbourline.test")
            world.viewModel.onCodeChanged("XNFAR-JVDPG")
            world.viewModel.onPasswordChanged("a completely new password")

            world.viewModel.submit()

            assertEquals(SignInMode.SignIn, world.viewModel.uiState.value.mode)
            assertTrue(
                world.viewModel.uiState.value.notice!!
                    .contains("signed out"),
            )
            assertEquals(
                "",
                world.viewModel.uiState.value.fields.password,
                "the new password must not sit in the sign-in field as if it had been typed there",
            )
        }

    @Test
    fun `an unusable code is reported without moving on`() =
        runTest {
            val world = world(api = FailingApi(AuthFailure.Rejected("That code is not usable. Ask for a new one.")))
            world.viewModel.onModeChanged(SignInMode.EnterCode)
            world.viewModel.onEmailChanged("ada@harbourline.test")
            world.viewModel.onCodeChanged("WRONG-CODE1")
            world.viewModel.onPasswordChanged("a completely new password")

            world.viewModel.submit()

            assertEquals(
                SignInMode.EnterCode,
                world.viewModel.uiState.value.mode,
                "staying put is what lets them retype it",
            )
            assertEquals("That code is not usable. Ask for a new one.", world.viewModel.uiState.value.error)
        }

    // -- Plumbing ---------------------------------------------------------------------------

    private fun SignInViewModel.fill(
        email: String,
        password: String,
    ) {
        onEmailChanged(email)
        onPasswordChanged(password)
    }

    private class World(
        val viewModel: SignInViewModel,
        val auth: AuthRepository,
        val store: RecordingStore,
    )

    private fun world(
        api: AuthApi = AcceptingApi(),
        hardwareBacked: Boolean = true,
    ): World {
        val store = RecordingStore(hardwareBacked)
        val auth = AuthRepository(store = store, api = api)
        return World(SignInViewModel(auth), auth, store)
    }

    private class RecordingStore(
        override val isHardwareBacked: Boolean,
    ) : SessionStore {
        var written: StoredSession? = null

        override suspend fun read(): StoredSession? = written

        override suspend fun write(session: StoredSession) {
            written = session
        }

        override suspend fun clear() {
            written = null
        }
    }

    // -- Changing your mind ------------------------------------------------------------------

    /**
     * The gap this was written for. Deleting revokes every session, so the sign-in screen is
     * the only door left — and it used to answer "email or password is wrong" to somebody
     * whose password was right and whose studio was still there.
     */
    @Test
    fun `signing in to a deleted studio offers it back rather than refusing`() =
        runTest {
            val world = world(api = PendingDeletionApi())

            world.viewModel.onEmailChanged("ada@harbourline.test")
            world.viewModel.onPasswordChanged("a long enough password")
            world.viewModel.submit()

            val state = world.viewModel.uiState.value
            assertEquals(1_788_000_000_000L, state.pendingDeletion, "the screen has to know there is a way back")
            assertNull(state.error, "the password was right, so this is not an error to apologise for")
        }

    @Test
    fun `restoring signs the studio back in`() =
        runTest {
            val world = world(api = PendingDeletionApi())

            world.viewModel.onEmailChanged("ada@harbourline.test")
            world.viewModel.onPasswordChanged("a long enough password")
            world.viewModel.submit()
            world.viewModel.restore()

            assertNotNull(world.store.written, "restoring has to leave the device signed in")
        }

    private class PendingDeletionApi : AuthApi by AcceptingApi() {
        override suspend fun signIn(
            email: String,
            password: String,
        ): StoredSession = throw AuthFailure.PendingDeletion(1_788_000_000_000L)

        override suspend fun restoreAccount(
            email: String,
            password: String,
        ): StoredSession =
            StoredSession(
                token = "restored",
                expiresAt = Long.MAX_VALUE,
                accountId = "account-1",
                email = email,
                name = "Ada Okafor",
                studioId = "studio-1",
                studioName = "Harbourline Photography",
            )
    }

    private class AcceptingApi : AuthApi {
        override suspend fun signIn(
            email: String,
            password: String,
        ) = session

        override suspend fun signUp(
            email: String,
            password: String,
            name: String,
            studioName: String,
        ) = session

        override suspend fun restoreAccount(
            email: String,
            password: String,
        ): StoredSession = error("unused")

        override suspend fun exportStudio(token: String): String = error("unused")

        override suspend fun deleteAccount(
            token: String,
            password: String,
        ): Long = error("unused")

        override suspend fun signOut(token: String) = Unit

        override suspend fun requestPasswordReset(email: String) = Unit

        override suspend fun resetPassword(
            email: String,
            code: String,
            newPassword: String,
        ) = Unit
    }

    private class FailingApi(
        private val failure: AuthFailure,
    ) : AuthApi {
        override suspend fun signIn(
            email: String,
            password: String,
        ): StoredSession = throw failure

        override suspend fun signUp(
            email: String,
            password: String,
            name: String,
            studioName: String,
        ): StoredSession = throw failure

        override suspend fun restoreAccount(
            email: String,
            password: String,
        ): StoredSession = error("unused")

        override suspend fun exportStudio(token: String): String = error("unused")

        override suspend fun deleteAccount(
            token: String,
            password: String,
        ): Long = error("unused")

        override suspend fun signOut(token: String) = Unit

        override suspend fun requestPasswordReset(email: String): Unit = throw failure

        override suspend fun resetPassword(
            email: String,
            code: String,
            newPassword: String,
        ): Unit = throw failure
    }

    private companion object {
        val session =
            StoredSession(
                token = "a-token",
                expiresAt = 1_900_000_000_000,
                accountId = "account-1",
                email = "ada@harbourline.test",
                name = "Ada Okafor",
                studioId = "studio-1",
                studioName = "Harbourline Photography",
            )
    }
}
