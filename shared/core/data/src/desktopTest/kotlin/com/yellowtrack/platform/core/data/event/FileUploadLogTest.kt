package com.yellowtrack.platform.core.data.event

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The log against a real file, because the only thing it is for is surviving a process.
 *
 * An in-memory test of this class would test the parts that do not matter. What matters is
 * what a second instance reads off the disk, and what it makes of a file a crash left half
 * written.
 */
class FileUploadLogTest {
    private val directory: File = Files.createTempDirectory("upload-log").toFile()
    private val file = File(directory, "camera-a.log")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    /** The whole purpose: a restarted process must not send the morning again. */
    @Test
    fun `entries are readable by a new instance`() =
        runTest {
            FileUploadLog(file).record("DSC_0001.JPG", delivered = true)

            assertEquals(setOf("DSC_0001.JPG"), FileUploadLog(file).handled())
        }

    /** A refusal is a photograph nobody will get, and it must not be retried forever either. */
    @Test
    fun `a refusal is remembered as firmly as a delivery`() =
        runTest {
            FileUploadLog(file).record("DSC_0002.JPG", delivered = false)

            assertEquals(setOf("DSC_0002.JPG"), FileUploadLog(file).handled())
        }

    /** Nothing has happened yet is not an error. */
    @Test
    fun `a log that does not exist yet is empty`() =
        runTest {
            assertTrue(FileUploadLog(File(directory, "never-written.log")).handled().isEmpty())
        }

    /**
     * Written as it goes, not at the end.
     *
     * A watcher that buffered would lose exactly the list it exists to keep, in exactly the
     * crash it exists to survive.
     */
    @Test
    fun `each entry reaches the disk as it is recorded`() =
        runTest {
            val log = FileUploadLog(file)
            log.record("DSC_0001.JPG", delivered = true)

            assertTrue(file.exists(), "nothing was written")
            assertTrue("DSC_0001.JPG" in file.readText(), file.readText())

            log.record("DSC_0002.JPG", delivered = true)

            assertEquals(2, file.readLines().size)
        }

    /**
     * The crash this is for leaves a partial line.
     *
     * Reading it as a complete name would suppress a photograph that was never actually
     * sent — the one failure this class must not have, since it is silent and permanent.
     */
    @Test
    fun `a half written final line is not treated as handled`() =
        runTest {
            file.writeText("delivered DSC_0001.JPG\ndeliv")

            val handled = FileUploadLog(file).handled()

            assertEquals(setOf("DSC_0001.JPG"), handled)
            assertFalse("deliv" in handled, "a torn line was read as a file name")
        }

    /** Cameras and card readers produce names with spaces in them. */
    @Test
    fun `a name containing spaces survives the round trip`() =
        runTest {
            FileUploadLog(file).record("Camera A 0001.JPG", delivered = true)

            assertEquals(setOf("Camera A 0001.JPG"), FileUploadLog(file).handled())
        }

    /**
     * A newline in a file name would write a second line, and every line is a claim that
     * some file needs no sending. Both Linux and macOS permit one.
     */
    @Test
    fun `a name containing a newline cannot forge an entry`() =
        runTest {
            FileUploadLog(file).record("evil\ndelivered DSC_0009.JPG", delivered = true)

            val handled = FileUploadLog(file).handled()

            assertEquals(1, handled.size, "a newline forged an entry: $handled")
            assertFalse("DSC_0009.JPG" in handled, "a photograph was suppressed by a crafted name")
        }
}
