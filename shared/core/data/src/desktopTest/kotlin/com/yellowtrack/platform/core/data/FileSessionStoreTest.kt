package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.auth.FileSessionStore
import com.yellowtrack.platform.core.data.auth.StoredSession
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The desktop credential store.
 *
 * The only one of the four that can be run here. Android, iOS and the browser are compiled
 * and unproven, which is stated where they are defined rather than left to be discovered.
 */
class FileSessionStoreTest {
    @Test
    fun `a session survives being written and read back`() =
        runTest {
            val store = FileSessionStore(file = tempFile())

            store.write(session)

            assertEquals(session, store.read())
        }

    @Test
    fun `nothing stored reads as signed out rather than failing`() =
        runTest {
            val store = FileSessionStore(file = tempFile())

            assertNull(store.read(), "a first launch has no session and that is not an error")
        }

    @Test
    fun `signing out removes the token from disk`() =
        runTest {
            val file = tempFile()
            val store = FileSessionStore(file = file)
            store.write(session)

            store.clear()

            assertNull(store.read())
            assertFalse(file.exists(), "the token must go, not merely stop being read")
        }

    @Test
    fun `only the owner can read the file`() =
        runTest {
            val file = tempFile()
            FileSessionStore(file = file).write(session)

            val permissions = Files.getPosixFilePermissions(file.toPath())

            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                permissions,
                "there is no hardware key on desktop, so file permissions are the whole of the " +
                    "protection. Group or other access here would hand the session to every " +
                    "account on the machine",
            )
        }

    @Test
    fun `only the owner can enter the directory holding it`() =
        runTest {
            val file = tempFile()
            FileSessionStore(file = file).write(session)

            val permissions = Files.getPosixFilePermissions(file.parentFile.toPath())

            assertFalse(
                PosixFilePermission.OTHERS_READ in permissions ||
                    PosixFilePermission.OTHERS_EXECUTE in permissions,
                "a readable directory lets somebody replace a file they cannot read",
            )
        }

    @Test
    fun `a corrupt file reads as signed out rather than crashing the launch`() =
        runTest {
            val file = tempFile()
            file.parentFile.mkdirs()
            file.writeText("{ this was half-written when the power went")

            assertNull(
                FileSessionStore(file = file).read(),
                "the cost of this is one sign-in; the alternative is an application that will " +
                    "not start",
            )
        }

    @Test
    fun `signing in again replaces the previous session`() =
        runTest {
            val store = FileSessionStore(file = tempFile())
            store.write(session)

            val second = session.copy(token = "a-second-token", accountId = "account-2")
            store.write(second)

            assertEquals(second, store.read(), "a stale token left behind would be used forever")
        }

    private fun tempFile(): File =
        File(
            Files.createTempDirectory("yellowtrack-session").toFile().also { it.deleteOnExit() },
            "nested/session.json",
        )

    private val session =
        StoredSession(
            token = "a-token",
            expiresAt = 1_800_000_000_000,
            accountId = "account-1",
            email = "ada@harbourline.test",
            name = "Ada Okafor",
            studioId = "studio-1",
            studioName = "Harbourline Photography",
        )
}
