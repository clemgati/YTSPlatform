package com.yellowtrack.platform.core.data.event

import com.yellowtrack.platform.core.common.time.AppClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What the watcher sends, and — mostly — what it refuses to send.
 *
 * Every test here is a photograph either lost or duplicated if the property fails, which is
 * why they are in `commonTest`: the logic is the same on every platform and there is no
 * reason only the JVM should be asked.
 */
class FolderWatchTest {
    // -- Settling ------------------------------------------------------------------------

    /**
     * The property the class exists for.
     *
     * A file appearing in the folder is not a photograph yet — the camera is still writing
     * into it. Sending on sight delivers a truncated JPEG, which decodes to a grey
     * half-frame that nothing downstream can distinguish from a real one.
     */
    @Test
    fun `a file that has only just appeared is not sent`() =
        runTest {
            val world = World()
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)

            val report = world.watch().sweep()

            assertEquals(0, report.sent, "nothing should have been sent on first sight")
            assertEquals(1, report.waiting)
            assertTrue(world.uploader.uploads.isEmpty())
        }

    /** A file still growing is still not a photograph, however many times it is looked at. */
    @Test
    fun `a file that is still growing is never sent`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.folder.put("DSC_0001.JPG", size = 1_000_000, modifiedAt = 1_000)

            repeat(5) {
                watch.sweep()
                world.advance(10_000)
                // The camera writes another megabyte between every look, so the fingerprint
                // never holds still.
                world.folder.put("DSC_0001.JPG", size = 1_000_000L * (it + 2), modifiedAt = 1_000L * (it + 2))
            }

            assertTrue(world.uploader.uploads.isEmpty(), "a growing file was sent: ${world.uploader.uploads}")
        }

    /** And once it stops, it goes. */
    @Test
    fun `a file that has held still is sent`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)

            watch.sweep()
            world.advance(3_000)
            val report = watch.sweep()

            assertEquals(1, report.sent, "a settled file should have been sent")
            assertEquals(listOf("DSC_0001.JPG"), world.uploader.uploads.map { it.fileName })
        }

    /** Settling is a duration, not a second look. */
    @Test
    fun `a second look too soon does not count as settled`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)

            watch.sweep()
            world.advance(200)
            val report = watch.sweep()

            assertEquals(0, report.sent, "200ms is not the two seconds this waits for")
            assertEquals(1, report.waiting)
        }

    /**
     * The read must agree with what was measured.
     *
     * The file is read after it has settled, not during, but nothing stops a camera writing
     * in between. Sending those bytes would mean sending a length that was never checked for
     * stability — the settling would have proved nothing about what actually went.
     */
    @Test
    fun `a file rewritten between settling and reading is not sent`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)
            watch.sweep()
            world.advance(3_000)

            // Settled by the listing, but longer by the time it is opened.
            world.folder.bytesReturned["DSC_0001.JPG"] = ByteArray(4_500_000)

            val report = watch.sweep()

            assertEquals(0, report.sent, "the bytes were not the bytes that settled")
            assertTrue(world.uploader.uploads.isEmpty())
        }

    // -- What is worth sending -----------------------------------------------------------

    /**
     * Raw files stay where they were shot.
     *
     * A tethered body writes a raw beside every JPEG. An attendee's phone cannot open a CR3,
     * and sending 60MB per frame would take the event's connection down for a photograph
     * nobody could look at.
     */
    @Test
    fun `raw files beside the jpegs are not sent`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)
            world.folder.put("DSC_0001.CR3", size = 60_000_000, modifiedAt = 1_000)
            world.folder.put("DSC_0001.NEF", size = 60_000_000, modifiedAt = 1_000)

            watch.sweep()
            world.advance(3_000)
            watch.sweep()

            assertEquals(listOf("DSC_0001.JPG"), world.uploader.uploads.map { it.fileName })
        }

    /** An empty file is a file the camera has created and not yet written to. */
    @Test
    fun `an empty file is not sent`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.folder.put("DSC_0001.JPG", size = 0, modifiedAt = 1_000)

            watch.sweep()
            world.advance(3_000)
            val report = watch.sweep()

            assertEquals(0, report.sent)
            assertTrue(world.uploader.uploads.isEmpty(), "an empty file should never be sent")
        }

    /** Editors and capture tools leave their own files in the folder. */
    @Test
    fun `hidden and in-progress files are ignored`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.folder.put(".DS_Store", size = 6_000, modifiedAt = 1_000)
            world.folder.put("_tmp_0001.jpg", size = 4_000_000, modifiedAt = 1_000)
            world.folder.put("DSC_0001.jpg", size = 4_000_000, modifiedAt = 1_000)

            watch.sweep()
            world.advance(3_000)
            watch.sweep()

            assertEquals(listOf("DSC_0001.jpg"), world.uploader.uploads.map { it.fileName })
        }

    // -- What is sent with it ------------------------------------------------------------

    /**
     * The capture time travels with the photograph, and it is the file's time.
     *
     * The server routes on this field. A laptop that lost its network for ten minutes
     * delivers a backlog belonging to slots that have since closed — sending "now" would
     * file every one of them under whoever happened to be sitting down when the connection
     * came back.
     */
    @Test
    fun `the capture time sent is the time the file was written`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_234_567)

            watch.sweep()
            world.advance(3_000)
            watch.sweep()

            val sent = world.uploader.uploads.single()

            assertEquals(1_234_567, sent.capturedAt)
        }

    /** A gallery should fill in the order the photographs were taken. */
    @Test
    fun `photographs are sent oldest first`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.folder.put("late.jpg", size = 4_000_000, modifiedAt = 3_000)
            world.folder.put("early.jpg", size = 4_000_000, modifiedAt = 1_000)
            world.folder.put("middle.jpg", size = 4_000_000, modifiedAt = 2_000)

            watch.sweep()
            world.advance(3_000)
            watch.sweep()

            assertEquals(listOf("early.jpg", "middle.jpg", "late.jpg"), world.uploader.uploads.map { it.fileName })
        }

    // -- Refusals and outages are opposite instructions -----------------------------------

    /**
     * A refusal is permanent, so the file is put down.
     *
     * Retrying something the server has said it will never accept is an infinite loop that
     * looks like activity.
     */
    @Test
    fun `a refused photograph is not offered again`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.uploader.answer = { UploadOutcome.Refused("that photograph was empty") }
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)

            watch.sweep()
            world.advance(3_000)
            val first = watch.sweep()
            world.advance(3_000)
            val second = watch.sweep()

            assertEquals(1, first.refused.size, "the refusal should be reported")
            assertEquals("DSC_0001.JPG", first.refused.single().path)
            assertEquals(1, world.uploader.uploads.size, "it should have been offered exactly once")
            assertTrue(second.refused.isEmpty(), "and not offered again on the next sweep")
        }

    /**
     * An outage is not.
     *
     * The opposite mistake, and the expensive one: dropping a photograph because a bucket
     * was briefly unreachable means it is gone, and the guest has left.
     */
    @Test
    fun `a photograph the server could not take is offered again`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.uploader.answer = { UploadOutcome.Unavailable("no object storage is configured") }
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)

            watch.sweep()
            world.advance(3_000)
            val first = watch.sweep()

            assertEquals(1, first.deferred)
            assertEquals(0, first.sent)

            // The bucket comes back.
            world.uploader.answer = { UploadOutcome.Stored("photo-1") }
            world.advance(3_000)
            val second = watch.sweep()

            assertEquals(1, second.sent, "it should have been tried again once storage returned")
            assertEquals(2, world.uploader.uploads.size)
        }

    /** A thrown exception is an outage, not a verdict on the photograph. */
    @Test
    fun `an upload that throws leaves the photograph queued`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.uploader.answer = { throw RuntimeException("connection reset") }
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)

            watch.sweep()
            world.advance(3_000)
            val report = watch.sweep()

            assertEquals(1, report.deferred, "a dropped connection must not discard the file")
            assertEquals(0, report.refused.size)
        }

    /**
     * A stall has to look different from work.
     *
     * The same reason `SyncReport.stuck` exists. Forty files deferring every two seconds
     * reads as a busy watcher to anyone glancing at it, so the ones that have stopped moving
     * are named.
     */
    @Test
    fun `a photograph that keeps failing is reported as stuck`() =
        runTest {
            val world = World()
            val watch = world.watch()
            world.uploader.answer = { UploadOutcome.Unavailable("no route to host") }
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)

            watch.sweep()
            world.advance(3_000)

            val reports = (1..5).map { watch.sweep() }

            assertTrue(reports.first().stuck.isEmpty(), "one failure is not a stall")
            assertEquals(listOf("DSC_0001.JPG"), reports.last().stuck, "five is")
        }

    // -- Across a restart ------------------------------------------------------------------

    /**
     * The folder outlives the process.
     *
     * A laptop that restarts mid-event sees a folder full of photographs it has already
     * sent. Without the log it sends the morning again: the studio's gallery doubles and
     * every attendee is mailed twice.
     */
    @Test
    fun `photographs already sent are not sent again after a restart`() =
        runTest {
            val world = World()
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)

            val before = world.watch()
            before.sweep()
            world.advance(3_000)
            before.sweep()
            assertEquals(1, world.uploader.uploads.size)

            // A new FolderWatch over the same folder and the same log: the process restarted,
            // the disk did not.
            val after = world.watch()
            after.sweep()
            world.advance(3_000)
            val report = after.sweep()

            assertEquals(0, report.sent, "the restarted watcher re-sent a photograph")
            assertEquals(1, world.uploader.uploads.size)
        }

    /** A refusal survives a restart too, or the loop simply restarts with the process. */
    @Test
    fun `a refusal survives a restart`() =
        runTest {
            val world = World()
            world.uploader.answer = { UploadOutcome.Refused("that photograph was empty") }
            world.folder.put("DSC_0001.JPG", size = 4_000_000, modifiedAt = 1_000)

            val before = world.watch()
            before.sweep()
            world.advance(3_000)
            before.sweep()

            val after = world.watch()
            after.sweep()
            world.advance(3_000)
            after.sweep()

            assertEquals(1, world.uploader.uploads.size, "the refused file was offered again after a restart")
        }

    // -- Fixtures ---------------------------------------------------------------------------

    private class FakeFolder : WatchedFolder {
        private val files = mutableMapOf<String, WatchedFile>()

        /** Overrides what [read] hands back, for the rewritten-underneath case. */
        val bytesReturned = mutableMapOf<String, ByteArray>()

        fun put(
            path: String,
            size: Long,
            modifiedAt: Long,
        ) {
            files[path] = WatchedFile(path, size, modifiedAt)
        }

        override fun list(): List<WatchedFile> = files.values.toList()

        override fun read(path: String): ByteArray =
            bytesReturned[path] ?: ByteArray(files.getValue(path).sizeBytes.toInt())
    }

    private class SentPhotograph(
        val fileName: String,
        val capturedAt: Long,
        val contentType: String,
    )

    private class FakeUploader : PhotographUploader {
        val uploads = mutableListOf<SentPhotograph>()
        var answer: () -> UploadOutcome = { UploadOutcome.Stored("photo-1") }

        override suspend fun upload(
            eventId: String,
            sourceKey: String,
            capturedAt: Long,
            fileName: String,
            contentType: String,
            bytes: ByteArray,
        ): UploadOutcome {
            uploads += SentPhotograph(fileName, capturedAt, contentType)

            return answer()
        }
    }

    /** In memory, but shared between two [FolderWatch]es — which is what a disk is here. */
    private class FakeLog : UploadLog {
        private val entries = mutableMapOf<String, Boolean>()

        override suspend fun handled(): Set<String> = entries.keys.toSet()

        override suspend fun record(
            path: String,
            delivered: Boolean,
        ) {
            entries[path] = delivered
        }
    }

    private class World {
        val folder = FakeFolder()
        val uploader = FakeUploader()
        val log = FakeLog()
        private var millis = 100_000L

        fun advance(by: Long) {
            millis += by
        }

        /** A fresh watcher over the same folder and log: what a restart looks like. */
        fun watch() =
            FolderWatch(
                folder = folder,
                uploader = uploader,
                log = log,
                eventId = "event-1",
                sourceKey = "Camera A",
                clock = AppClock { Instant.fromEpochMilliseconds(millis) },
            )
    }
}
