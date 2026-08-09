package com.yellowtrack.platform.core.data.event

import com.yellowtrack.platform.core.common.time.AppClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The loop that keeps ingest running for the length of an event.
 *
 * Two of these are correctness rather than tidiness: one folder must never have two loops on
 * it, and a sweep that throws must not end ingest for the rest of the day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngestServiceTest {
    /**
     * The rule that stops every photograph arriving twice.
     *
     * Two loops on one folder both sweep, both find the same settled file unhandled — the log
     * is written only after an upload succeeds — and both send it. The gallery doubles and
     * every attendee is mailed twice.
     */
    @Test
    fun `watching a source that is already watched does not start a second loop`() =
        runTest {
            val world = World(this)
            val service = world.service()

            assertTrue(service.watch("event-1", "Camera A", folder()), "the first watch should start")
            assertFalse(service.watch("event-1", "Camera A", folder()), "the second should be refused")

            advanceTimeBy(7.seconds)
            runCurrent()

            // One loop sweeping, not two racing.
            assertEquals(1, world.platform.foldersOpened, "a second folder was opened for the same source")
        }

    /** And stopping frees the source, or a station reopened mid-event would never ingest. */
    @Test
    fun `a source can be watched again after it is stopped`() =
        runTest {
            val world = World(this)
            val service = world.service()

            service.watch("event-1", "Camera A", folder())
            service.stop("Camera A")

            assertTrue(service.watch("event-1", "Camera A", folder()), "the source stayed claimed after stopping")
        }

    /**
     * The failure that must not end ingest.
     *
     * A folder unmounted, a disk gone read-only, a permission withdrawn. Stopping would mean
     * ingest ending silently while the screen still showed a station open.
     */
    @Test
    fun `a sweep that throws does not stop the loop`() =
        runTest {
            val world = World(this)
            world.folder.failNextReads = 1
            val service = world.service()

            service.watch("event-1", "Camera A", folder())

            // Only the first sweep, which is the one that throws. Advancing past the interval
            // would run the second as well and that one succeeds, clearing the failure before
            // it could be read — which is how this assertion first failed against correct code.
            runCurrent()
            assertNotNull(
                service.status.value["Camera A"]?.lastSweepFailed,
                "the failure should have been recorded rather than swallowed",
            )

            // The folder comes back, and so does ingest.
            world.folder.put("DSC_0001.JPG", size = 4_000, modifiedAt = 1_000)
            advanceTimeBy(9.seconds)
            runCurrent()

            assertEquals(1, world.uploads(), "the loop stopped after one bad sweep")
        }

    /** A sweep that works clears the warning, or one unmount marks the screen all day. */
    @Test
    fun `a later good sweep clears the failure`() =
        runTest {
            val world = World(this)
            world.folder.failNextReads = 1
            val service = world.service()

            service.watch("event-1", "Camera A", folder())
            runCurrent()
            assertNotNull(service.status.value["Camera A"]?.lastSweepFailed)

            advanceTimeBy(5.seconds)
            runCurrent()

            assertNull(service.status.value["Camera A"]?.lastSweepFailed, "the warning outlived the problem")
        }

    // -- What the studio reads ---------------------------------------------------------------

    /**
     * Cumulative, not per sweep.
     *
     * A sweep happens every two seconds and usually does nothing, so a per-sweep count reads
     * "0 sent" almost always — true, useless, and indistinguishable from ingest being broken.
     */
    @Test
    fun `the sent count accumulates across sweeps`() =
        runTest {
            val world = World(this)
            val service = world.service()
            service.watch("event-1", "Camera A", folder())

            world.folder.put("DSC_0001.JPG", size = 4_000, modifiedAt = 1_000)
            advanceTimeBy(9.seconds)
            runCurrent()
            assertEquals(1, service.status.value["Camera A"]?.sent)

            world.folder.put("DSC_0002.JPG", size = 4_000, modifiedAt = 2_000)
            advanceTimeBy(9.seconds)
            runCurrent()

            assertEquals(2, service.status.value["Camera A"]?.sent, "the count reset instead of accumulating")
        }

    /**
     * A refusal is a photograph nobody receives, and it appears in exactly one sweep — the
     * file is never offered again. Dropping it from the status would erase the only notice
     * anybody gets.
     */
    @Test
    fun `refusals are kept rather than replaced by the next sweep`() =
        runTest {
            val world = World(this)
            world.uploader.answer = { UploadOutcome.Refused("that photograph was empty") }
            val service = world.service()
            service.watch("event-1", "Camera A", folder())

            world.folder.put("DSC_0001.JPG", size = 4_000, modifiedAt = 1_000)
            advanceTimeBy(9.seconds)
            runCurrent()

            assertEquals(
                1,
                service.status.value["Camera A"]
                    ?.refused
                    ?.size,
            )

            // Several more sweeps in which nothing at all happens.
            advanceTimeBy(11.seconds)
            runCurrent()

            val status = assertNotNull(service.status.value["Camera A"])
            assertEquals(1, status.refused.size, "the refusal was forgotten by a later empty sweep")
            assertTrue(status.needsAttention, "a refused photograph should be worth looking at")
        }

    /**
     * Stopping keeps the record.
     *
     * A photographer closing a station still wants to see that it sent 214 and that two were
     * refused. Clearing on stop deletes that at the moment somebody goes looking.
     */
    @Test
    fun `stopping a watch leaves its status behind`() =
        runTest {
            val world = World(this)
            val service = world.service()
            service.watch("event-1", "Camera A", folder())

            world.folder.put("DSC_0001.JPG", size = 4_000, modifiedAt = 1_000)
            advanceTimeBy(9.seconds)
            runCurrent()

            service.stop("Camera A")
            advanceTimeBy(5.seconds)
            runCurrent()

            assertEquals(1, service.status.value["Camera A"]?.sent, "the record went with the watch")
            assertFalse(service.isWatching("Camera A"))
        }

    /** Stopping actually stops it, or a closed station keeps claiming photographs. */
    @Test
    fun `a stopped watch sends nothing more`() =
        runTest {
            val world = World(this)
            val service = world.service()
            service.watch("event-1", "Camera A", folder())
            advanceTimeBy(3.seconds)
            runCurrent()

            service.stop("Camera A")

            world.folder.put("DSC_0001.JPG", size = 4_000, modifiedAt = 1_000)
            advanceTimeBy(20.seconds)
            runCurrent()

            assertEquals(0, world.uploads(), "a stopped watch was still uploading")
        }

    /** Two cameras at one event are two independent watches. */
    @Test
    fun `two sources are watched independently`() =
        runTest {
            val world = World(this)
            val service = world.service()

            assertTrue(service.watch("event-1", "Camera A", folder("Camera A")))
            assertTrue(service.watch("event-1", "Camera B", folder("Camera B")))

            assertEquals(2, service.status.value.size)
            assertTrue(service.isWatching("Camera A"))
            assertTrue(service.isWatching("Camera B"))
        }

    // -- Fixtures --------------------------------------------------------------------------

    private fun folder(name: String = "Camera A") = ChosenFolder(path = "/Volumes/Capture/$name", name = name)

    /**
     * The watch loop never finishes, so it runs in `backgroundScope` — which `runTest`
     * cancels at the end instead of waiting for. Launching it in the test's own scope makes
     * every test hang until its timeout, which is how this fixture was first written.
     */
    private class World(
        private val test: TestScope,
    ) {
        val folder = FakeFolder()
        val uploader = FakeUploader()
        val platform = FakePlatform(folder)

        fun uploads() = uploader.count

        fun service() =
            IngestService(
                platform = platform,
                uploader = uploader,
                scope = test.backgroundScope,
                // Advances with the test scheduler, so files settle when time passes rather
                // than never.
                clock = AppClock { Instant.fromEpochMilliseconds(100_000L + test.testScheduler.currentTime) },
            )
    }

    private class FakeFolder : WatchedFolder {
        private val files = mutableMapOf<String, WatchedFile>()
        var failNextReads = 0

        fun put(
            path: String,
            size: Long,
            modifiedAt: Long,
        ) {
            files[path] = WatchedFile(path, size, modifiedAt)
        }

        override fun list(): List<WatchedFile> {
            if (failNextReads > 0) {
                failNextReads--
                throw IllegalStateException("the folder is not there")
            }

            return files.values.toList()
        }

        override fun read(path: String): ByteArray = ByteArray(files.getValue(path).sizeBytes.toInt())
    }

    private class FakeUploader : PhotographUploader {
        var count = 0
        var answer: () -> UploadOutcome = { UploadOutcome.Stored("photo") }

        override suspend fun upload(
            eventId: String,
            sourceKey: String,
            capturedAt: Long,
            fileName: String,
            contentType: String,
            bytes: ByteArray,
        ): UploadOutcome {
            count++

            return answer()
        }
    }

    private class FakePlatform(
        private val folder: WatchedFolder,
    ) : IngestPlatform {
        var foldersOpened = 0
        private val logs = mutableMapOf<String, UploadLog>()

        override val canWatchFolders: Boolean = true

        override suspend fun chooseFolder(): ChosenFolder? = null

        override fun folderAt(path: String): WatchedFolder {
            foldersOpened++

            return folder
        }

        override fun logFor(
            folderPath: String,
            eventId: String,
            sourceKey: String,
        ): UploadLog = logs.getOrPut("$folderPath|$eventId|$sourceKey") { InMemoryLog() }
    }

    private class InMemoryLog : UploadLog {
        private val entries = mutableSetOf<String>()

        override suspend fun handled(): Set<String> = entries.toSet()

        override suspend fun record(
            path: String,
            delivered: Boolean,
        ) {
            entries += path
        }
    }
}
