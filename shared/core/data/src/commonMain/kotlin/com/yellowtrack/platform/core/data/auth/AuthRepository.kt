package com.yellowtrack.platform.core.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why an attempt was refused, in a form a screen can act on. */
sealed class AuthFailure(
    message: String,
) : Exception(message) {
    /** Wrong password, unknown address — the server does not say which, and neither does this. */
    data object BadCredentials : AuthFailure("That email or password is not right.")

    data object EmailAlreadyRegistered : AuthFailure("That email address already has an account.")

    data class Rejected(
        val reason: String,
    ) : AuthFailure(reason)

    /**
     * The server could not be reached at all.
     *
     * Kept apart from the others because it is the one a studio can do something about, and
     * because on a shoot day with no signal it is by far the most likely.
     */
    data object Unreachable : AuthFailure("Could not reach the server. Check your connection.")
}

/** What the server is asked to do. Implemented over HTTP in `core:network`. */
interface AuthApi {
    suspend fun signIn(
        email: String,
        password: String,
    ): StoredSession

    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        studioName: String,
    ): StoredSession

    /**
     * Ends the session server-side.
     *
     * Best effort by design — see [AuthRepository.signOut] on why a failure here must not
     * keep somebody signed in on the device in front of them.
     */
    suspend fun signOut(token: String)
}

/** Whether anybody is signed in, as a screen needs to know it. */
sealed interface SessionState {
    /** Before the stored session has been read. Distinct from signed out, so the first frame
     *  does not flash a sign-in screen at somebody who is already signed in. */
    data object Unknown : SessionState

    data object SignedOut : SessionState

    data class SignedIn(
        val session: StoredSession,
    ) : SessionState
}

/**
 * Who is signed in, and how that changes.
 *
 * The single place the token is read from and written to, so nothing else has to know which
 * of the four stores it is talking to or that there are four.
 */
class AuthRepository(
    private val store: SessionStore,
    private val api: AuthApi,
) {
    private val state = MutableStateFlow<SessionState>(SessionState.Unknown)

    val session: StateFlow<SessionState> = state.asStateFlow()

    /** Whether the token is held somewhere the device protects with a key. */
    val isHardwareBacked: Boolean get() = store.isHardwareBacked

    /**
     * Reads whatever was stored, at launch.
     *
     * An expired token is treated as signed out here rather than waiting for the server to
     * say so, because that answer needs a connection and this has to work on a device that
     * has none.
     */
    suspend fun restore(now: Long) {
        val stored = store.read()

        state.value =
            when {
                stored == null -> SessionState.SignedOut
                stored.expiresAt <= now -> {
                    store.clear()
                    SessionState.SignedOut
                }
                else -> SessionState.SignedIn(stored)
            }
    }

    suspend fun signIn(
        email: String,
        password: String,
    ) {
        adopt(api.signIn(email, password))
    }

    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        studioName: String,
    ) {
        adopt(api.signUp(email, password, name, studioName))
    }

    /**
     * Signs out here first, and tells the server afterwards.
     *
     * The order matters. A studio pressing sign out on a phone it is about to hand over
     * needs the token gone from *this* device, and making that conditional on reaching the
     * server would leave it in place exactly when there is no signal. The server-side
     * revocation is attempted and its failure ignored; the session expires on its own if
     * the call never lands.
     */
    suspend fun signOut() {
        val current = (state.value as? SessionState.SignedIn)?.session

        store.clear()
        state.value = SessionState.SignedOut

        current?.let { runCatching { api.signOut(it.token) } }
    }

    /** The token for the signed-in device, or null. Read per request, never cached. */
    suspend fun token(): String? = (state.value as? SessionState.SignedIn)?.session?.token

    private suspend fun adopt(session: StoredSession) {
        store.write(session)
        state.value = SessionState.SignedIn(session)
    }
}
