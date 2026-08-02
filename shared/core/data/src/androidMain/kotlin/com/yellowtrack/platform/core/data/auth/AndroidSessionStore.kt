package com.yellowtrack.platform.core.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json

/**
 * The Android session, encrypted with a key held in the device's keystore.
 *
 * `EncryptedSharedPreferences` wraps the preference file with an AES key the application
 * cannot extract — it lives in hardware-backed storage where the device has it. So a
 * rooted-but-locked phone does not hand over the token, which is the difference
 * [isHardwareBacked] is reporting.
 *
 * **Compiled, not run.** No one has opened this application on a phone. The API shape is
 * ordinary and the failure mode is loud — `EncryptedSharedPreferences.create` throws rather
 * than silently falling back to plaintext — but treat it as unproven until someone signs in
 * on a device.
 */
class AndroidSessionStore(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionStore {
    override val isHardwareBacked: Boolean = true

    private val preferences by lazy {
        val masterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun read(): StoredSession? {
        val stored = preferences.getString(SESSION_KEY, null) ?: return null

        // Undecryptable reads as signed-out rather than throwing: a restored backup or a
        // reset keystore leaves ciphertext no key can open, and the remedy is signing in
        // again rather than an application that will not start.
        return runCatching { json.decodeFromString<StoredSession>(stored) }.getOrNull()
    }

    override suspend fun write(session: StoredSession) {
        preferences.edit().putString(SESSION_KEY, json.encodeToString(session)).commit()
    }

    override suspend fun clear() {
        preferences.edit().remove(SESSION_KEY).commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "yellowtrack.session"
        const val SESSION_KEY = "session"
    }
}
