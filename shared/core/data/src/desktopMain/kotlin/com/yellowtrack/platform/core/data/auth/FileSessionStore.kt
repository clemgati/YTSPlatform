package com.yellowtrack.platform.core.data.auth

import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * The desktop session, in a file only its owner can read.
 *
 * There is no hardware-held key here and [isHardwareBacked] says so. The protection is file
 * permissions: `rw-------`, set on the file *and* on the directory above it, because a
 * readable directory lets somebody replace a file they cannot read.
 *
 * That is weaker than Keychain and it is the honest ceiling for a desktop JVM without
 * pulling in a native keyring binding. Anyone with the account's own login can read this;
 * what it stops is other accounts on a shared machine.
 *
 * On Windows the POSIX permission calls are unsupported and are skipped rather than failing
 * — NTFS inherits per-user ACLs on the profile directory, which is a different mechanism
 * reaching a similar place.
 */
class FileSessionStore(
    private val file: File = defaultFile(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionStore {
    override val isHardwareBacked: Boolean = false

    override suspend fun read(): StoredSession? {
        if (!file.exists()) return null

        // A corrupt or half-written file reads as "not signed in" rather than crashing the
        // launch. The cost is one sign-in; the alternative is an application that will not
        // start.
        return runCatching { json.decodeFromString<StoredSession>(file.readText()) }.getOrNull()
    }

    override suspend fun write(session: StoredSession) {
        file.parentFile?.let { parent ->
            parent.mkdirs()
            parent.restrictToOwner(directory = true)
        }

        // Created before it is written to, so the permissions are narrowed before the token
        // is ever on disk. Writing first and chmod-ing after leaves a window where the file
        // is world-readable.
        if (!file.exists()) file.createNewFile()
        file.restrictToOwner(directory = false)
        file.writeText(json.encodeToString(session))
    }

    override suspend fun clear() {
        file.delete()
    }

    private fun File.restrictToOwner(directory: Boolean) {
        runCatching {
            val permissions =
                if (directory) {
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    )
                } else {
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                }

            Files.setPosixFilePermissions(toPath(), permissions)
        }
    }

    companion object {
        /** Beside the database, in the same per-user application directory. */
        fun defaultFile(): File {
            val home = System.getProperty("user.home").orEmpty()
            val osName = System.getProperty("os.name").orEmpty().lowercase()

            val base =
                when {
                    osName.contains("mac") -> File(home, "Library/Application Support")
                    osName.contains("win") -> File(System.getenv("APPDATA") ?: home)
                    else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share")
                }

            return File(File(base, "YellowTrack"), "session.json")
        }
    }
}
