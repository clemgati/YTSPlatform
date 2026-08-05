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
     * Asks for a reset code.
     *
     * Returns normally whether or not the address has an account — the server answers the
     * same either way, and a client that inferred otherwise would hand back the
     * account-existence answer the server refuses to give.
     */
    suspend fun requestPasswordReset(email: String)

    /** Sets a new password with an emailed code. Throws [AuthFailure] if the code is not usable. */
    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    )

    /**
     * Ends the session server-side.
     *
     * Best effort by design — see [AuthRepository.signOut] on why a failure here must not
     * keep somebody signed in on the device in front of them.
     */
    suspend fun signOut(token: String)

    /**
     * The studio's whole record, as the JSON the server sends.
     *
     * Returned as text rather than parsed. Nothing here reads it — it is written to a file
     * for the studio to keep — and a client that decoded it into models would have to be
     * taught every entity again, and would start dropping the ones it had not been taught.
     */
    suspend fun exportStudio(token: String): String

    /**
     * Asks for the studio and everything in it to be deleted.
     *
     * Returns when the records stop being recoverable. Throws [AuthFailure] if the password
     * is wrong, which is the only thing standing between a borrowed laptop and a deleted
     * business.
     */
    suspend fun deleteAccount(
        token: String,
        password: String,
    ): Long
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
    suspend fun requestPasswordReset(email: String) {
        api.requestPasswordReset(email)
    }

    /**
     * Does not sign in afterwards.
     *
     * The reset revoked every session, so there is nothing to resume; the studio signs in
     * with the new password like anybody else. Quietly issuing a session here would also
     * mean a code from an email was enough to be signed in, which is a lower bar than the
     * password it just replaced.
     */
    suspend fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
    ) {
        api.resetPassword(email, code, newPassword)
    }

    suspend fun signOut() {
        val current = (state.value as? SessionState.SignedIn)?.session

        store.clear()
        state.value = SessionState.SignedOut

        current?.let { runCatching { api.signOut(it.token) } }
    }

    /**
     * The studio's whole record, for the device to write somewhere.
     *
     * Throws if nobody is signed in, which cannot happen from a screen that is only reachable
     * when somebody is.
     */
    suspend fun exportStudio(): String {
        val token = token() ?: throw AuthFailure.Rejected("You are not signed in.")
        return api.exportStudio(token)
    }

    /**
     * Deletes the studio, and signs this device out because the server already has.
     *
     * The order is the opposite of [signOut]'s, and deliberately so. Signing out clears the
     * device first so a failing server cannot strand somebody signed in; here the server has
     * to succeed first, because clearing the device on a request that was refused would
     * report a deleted studio that is still there — and the studio would sign back in to
     * find it, having been told it was gone.
     */
    suspend fun deleteAccount(password: String): Long {
        val token = token() ?: throw AuthFailure.Rejected("You are not signed in.")
        val purgeAfter = api.deleteAccount(token, password)

        store.clear()
        state.value = SessionState.SignedOut

        return purgeAfter
    }

    /** The token for the signed-in device, or null. Read per request, never cached. */
    suspend fun token(): String? = (state.value as? SessionState.SignedIn)?.session?.token

    private suspend fun adopt(session: StoredSession) {
        store.write(session)
        state.value = SessionState.SignedIn(session)
    }
}
