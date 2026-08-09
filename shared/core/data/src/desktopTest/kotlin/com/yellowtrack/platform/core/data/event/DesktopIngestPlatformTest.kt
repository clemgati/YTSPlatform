package com.yellowtrack.platform.core.data.event

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The parts of the desktop platform that do not need somebody to click something.
 *
 * `chooseFolder` opens a modal dialog and is not exercised here — there is no headless way to
 * press a button in it, and a test that stubbed the dialog would be testing the stub. What is
 * covered is everything the dialog's answer is then used for, which is where the mistakes
 * that matter live: reading the right folder, and keeping the right logs apart.
 */
class DesktopIngestPlatformTest {
    private val directory: File = Files.createTempDirectory("ingest-platform").toFile()
    private val platform = DesktopIngestPlatform(logDirectory = File(directory, "logs"))

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a folder reports what is in it`() {
        val capture = File(directory, "Camera A").apply { mkdirs() }
        File(capture, "DSC_0001.JPG").writeBytes(ByteArray(2_048))

        val listed = platform.folderAt(capture.absolutePath).list()

        assertEquals(listOf("DSC_0001.JPG"), listed.map { it.path })
        assertEquals(2_048, listed.single().sizeBytes)
    }

    /**
     * The same folder used again for a later event must start empty.
     *
     * A studio reuses `~/Capture` every weekend. If the log were keyed on the folder alone,
     * the second event would open believing every file in it had already been sent — and the
     * whole morning would be skipped, silently, with the screen reporting nothing wrong.
     */
    @Test
    fun `a folder reused for another event gets a different log`() =
        runTest {
            val path = "/Volumes/Capture/Camera A"

            platform.logFor(path, "event-1", "Camera A").record("DSC_0001.JPG", delivered = true)

            assertTrue(
                platform.logFor(path, "event-2", "Camera A").handled().isEmpty(),
                "a second event inherited the first event's sent list",
            )
        }

    /** And two cameras writing into one folder — which happens — keep separate lists. */
    @Test
    fun `two sources on one folder get different logs`() =
        runTest {
            val path = "/Volumes/Capture/Shared"

            platform.logFor(path, "event-1", "Camera A").record("DSC_0001.JPG", delivered = true)

            assertTrue(platform.logFor(path, "event-1", "Camera B").handled().isEmpty())
        }

    /** The same three things must find the same log, or a restart re-sends everything. */
    @Test
    fun `the same folder event and source find the same log`() =
        runTest {
            val path = "/Volumes/Capture/Camera A"

            platform.logFor(path, "event-1", "Camera A").record("DSC_0001.JPG", delivered = true)

            assertEquals(
                setOf("DSC_0001.JPG"),
                platform.logFor(path, "event-1", "Camera A").handled(),
                "a fresh log object could not read what the last one wrote",
            )
        }

    /**
     * Two different folders must not collide even when the event and source match.
     *
     * A photographer who repoints a station at a different card would otherwise carry the
     * first card's sent list across, and the new card's files would be skipped.
     */
    @Test
    fun `different folders get different logs`() =
        runTest {
            platform.logFor("/Volumes/Card1/DCIM", "event-1", "Camera A").record("DSC_0001.JPG", delivered = true)

            assertTrue(
                platform.logFor("/Volumes/Card2/DCIM", "event-1", "Camera A").handled().isEmpty(),
                "two cards shared one sent list",
            )
        }

    /** Names with separators and spaces in them must not escape the log directory. */
    @Test
    fun `a source name with awkward characters stays inside the log directory`() =
        runTest {
            val logs = File(directory, "logs")

            platform.logFor("/Volumes/Capture", "../../etc", "../Camera A").record("DSC_0001.JPG", delivered = true)

            val written = logs.listFiles().orEmpty()
            assertEquals(1, written.size, "expected exactly one log file: ${written.map { it.name }}")
            assertEquals(
                logs.canonicalFile,
                written.single().canonicalFile.parentFile,
                "a log was written outside the log directory",
            )
            assertNotEquals(true, written.single().name.contains(".."), written.single().name)
        }
}
