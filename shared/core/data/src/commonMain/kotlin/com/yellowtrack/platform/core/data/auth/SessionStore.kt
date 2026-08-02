package com.yellowtrack.platform.core.data.auth

import kotlinx.serialization.Serializable

/**
 * The signed-in device's session, as it is kept between launches.
 *
 * The token is the whole of the credential — the server stores only its digest, so a
 * device that loses this has to sign in again and nothing can recover it.
 */
@Serializable
data class StoredSession(
    val token: String,
    val expiresAt: Long,
    val accountId: String,
    val email: String,
    val name: String,
    val studioId: String,
    val studioName: String,
)

/**
 * Where the token is kept, which is a different answer on every platform.
 *
 * Deliberately its own thing rather than a corner of the database. The database is a
 * studio's business records; this is a credential, and the two want different storage,
 * different backup behaviour, and different treatment when somebody signs out.
 *
 * ## What each platform actually gives you
 *
 * The implementations are honest about differing in kind, not just in API. Keychain and
 * EncryptedSharedPreferences are backed by hardware-held keys. A desktop file is protected
 * by file permissions and nothing else. Browser storage is readable by any script that gets
 * onto the page.
 *
 * That is a real difference in what a stolen device costs a studio, and it is why
 * [isHardwareBacked] exists — not to make decisions here, but so a screen can tell the
 * truth about it rather than implying a guarantee the platform is not making.
 */
interface SessionStore {
    suspend fun read(): StoredSession?

    suspend fun write(session: StoredSession)

    suspend fun clear()

    /**
     * Whether the platform protects this with a key the application cannot extract.
     *
     * False on desktop and in a browser. Signing out still works there; what differs is
     * what somebody with the device can do without the password.
     */
    val isHardwareBacked: Boolean
}
