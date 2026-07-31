package com.yellowtrack.platform.core.data.auth

import kotlinx.browser.localStorage
import kotlinx.serialization.json.Json

/**
 * The browser session, in `localStorage`.
 *
 * [isHardwareBacked] is false and that is not a technicality. A browser offers no key the
 * page cannot read: anything with script access to this origin can read this token, and
 * that includes a compromised dependency. There is no browser storage that fixes this —
 * `sessionStorage` merely forgets sooner, and a cookie would move the problem rather than
 * solve it.
 *
 * It is implemented anyway because the alternative is a web build that cannot sign in at
 * all. What it must not do is imply a protection it does not have, which is why the flag
 * exists and why a screen can say so.
 */
class BrowserSessionStore(
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionStore {
    override val isHardwareBacked: Boolean = false

    override suspend fun read(): StoredSession? {
        val stored = localStorage.getItem(SESSION_KEY) ?: return null
        return runCatching { json.decodeFromString<StoredSession>(stored) }.getOrNull()
    }

    override suspend fun write(session: StoredSession) {
        localStorage.setItem(SESSION_KEY, json.encodeToString(session))
    }

    override suspend fun clear() {
        localStorage.removeItem(SESSION_KEY)
    }

    private companion object {
        const val SESSION_KEY = "yellowtrack.session"
    }
}
