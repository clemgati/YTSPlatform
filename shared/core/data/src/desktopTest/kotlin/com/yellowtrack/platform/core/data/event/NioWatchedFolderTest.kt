package com.yellowtrack.platform.core.data.event

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The folder against a real disk.
 *
 * [FolderWatch] is tested without one because its decisions do not need a file system. What
 * needs a real one is this: that a growing file really does report a growing length, and
 * that a folder which disappears mid-event does not take the watcher down with it.
 */
class NioWatchedFolderTest {
    private val directory: File = Files.createTempDirectory("watched").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `files are listed with their length`() {
        File(directory, "DSC_0001.JPG").writeBytes(ByteArray(2_048))

        val listed = NioWatchedFolder(directory).list()

        assertEquals(1, listed.size)
        assertEquals("DSC_0001.JPG", listed.single().path)
        assertEquals(2_048, listed.single().sizeBytes)
    }

    /**
     * The signal the whole settling rule rests on.
     *
     * If a partially written file reported its eventual length rather than its current one,
     * every truncated photograph would look settled on first sight and the watcher would
     * send half-frames all day.
     */
    @Test
    fun `a file that grows reports a larger length`() {
        val file = File(directory, "DSC_0001.JPG")
        file.writeBytes(ByteArray(1_024))
        val first = NioWatchedFolder(directory).list().single().sizeBytes

        file.appendBytes(ByteArray(1_024))
        val second = NioWatchedFolder(directory).list().single().sizeBytes

        assertEquals(1_024, first)
        assertEquals(2_048, second, "a growing file must report its current length, not its final one")
    }

    /** Sub-folders are the capture tool's business. A capture folder is flat. */
    @Test
    fun `directories are not listed as files`() {
        File(directory, "previews").mkdirs()
        File(directory, "DSC_0001.JPG").writeBytes(ByteArray(16))

        assertEquals(listOf("DSC_0001.JPG"), NioWatchedFolder(directory).list().map { it.path })
    }

    /**
     * A card reader pulled, or a network volume dropped, in the middle of an event.
     *
     * Empty rather than an exception, so the sweep carries on and looks again next time
     * instead of the watcher stopping for the rest of the day.
     */
    @Test
    fun `a folder that is not there lists as empty`() {
        val missing = File(directory, "unplugged")

        assertTrue(NioWatchedFolder(missing).list().isEmpty())
    }

    /**
     * Names come from `list` today, so this cannot currently be reached with anything else —
     * which is exactly when a guard is cheap to add and impossible to add later.
     */
    @Test
    fun `a path that leaves the folder is refused`() {
        val folder = NioWatchedFolder(directory)

        assertFailsWith<IllegalArgumentException> { folder.read("../secrets.txt") }
        assertFailsWith<IllegalArgumentException> { folder.read("nested/file.jpg") }
    }

    @Test
    fun `a listed file can be read back`() {
        File(directory, "DSC_0001.JPG").writeBytes(byteArrayOf(1, 2, 3, 4))

        assertEquals(4, NioWatchedFolder(directory).read("DSC_0001.JPG").size)
    }
}
