package com.yellowtrack.platform.core.data.event

import com.yellowtrack.platform.core.common.time.AppClock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** One photograph the server will never accept, and what it said. */
data class RefusedPhotograph(
    val path: String,
    val reason: String,
)

/**
 * What one pass over the folder did.
 *
 * Deliberately shaped like `SyncReport`: the counts that mean "fine" are numbers, and the
 * two that mean "a photograph is not going to arrive" are lists with names in them. A studio
 * watching an event needs to know that in the moment — after the guests have gone home it is
 * too late to take the photograph again.
 */
data class SweepReport(
    /** Sent and acknowledged this pass. */
    val sent: Int,
    /**
     * Seen, but not sent: still being written, or too new to trust yet.
     *
     * The normal state of a folder during a burst, and not a problem — the next sweep picks
     * them up.
     */
    val waiting: Int,
    /** Files the server will never take. Each one is a photograph nobody will receive. */
    val refused: List<RefusedPhotograph> = emptyList(),
    /** Failed for a reason that may pass. Still queued. */
    val deferred: Int = 0,
    /**
     * Files that have failed repeatedly and are no longer plausibly "about to work".
     *
     * The same reason `SyncReport.stuck` exists. A watcher that keeps saying "0 sent, 40
     * deferred" every two seconds looks busy, and a photographer glancing at it sees
     * activity rather than a stall — so the ones that have stopped moving are named.
     */
    val stuck: List<String> = emptyList(),
) {
    /** Nothing to report and nothing outstanding. */
    val isQuiet: Boolean
        get() = sent == 0 && waiting == 0 && refused.isEmpty() && deferred == 0
}

/**
 * The folder tethered capture writes to, watched.
 *
 * ADR 0013 decision 5. A photographer shoots into a folder and this sends what appears, so
 * that an attendee has their photograph before they have left the room.
 *
 * Three things make this harder than "upload new files", and they are the whole class:
 *
 * **A file exists before it is finished.** Tethered capture creates the file and then writes
 * megabytes into it. Uploading on sight delivers a truncated JPEG that decodes to a grey
 * half-frame — which nothing downstream can tell from a real photograph, because it is a
 * valid file that is simply wrong. So nothing is sent until its size and modification time
 * have been unchanged for [settleFor]. The server's empty-body refusal only catches the
 * degenerate version of this; everything between empty and complete has to be caught here.
 *
 * **A refusal and an outage are opposite instructions.** The server distinguishes them
 * deliberately — a 4xx means this file will never be accepted, a 503 means try again — and
 * this honours it. Getting it backwards either loses photographs or loops on one forever.
 *
 * **The folder outlives the process.** A laptop that restarts mid-event must not send the
 * morning again, which is what [UploadLog] is for.
 */
class FolderWatch(
    private val folder: WatchedFolder,
    private val uploader: PhotographUploader,
    private val log: UploadLog,
    private val eventId: String,
    private val sourceKey: String,
    private val clock: AppClock = AppClock.System,
    /**
     * How long a file must hold still before it is believed to be complete.
     *
     * Two seconds is chosen against the write, not the shutter: a tethered body writing a
     * 30MB raw over USB pauses for tens of milliseconds, not seconds. Long enough to cover
     * that, short enough that "immediate review" stays true.
     */
    private val settleFor: Duration = 2.seconds,
    /** Failures on one file before it is called stuck rather than merely deferred. */
    private val attemptsBeforeStuck: Int = 5,
) {
    private data class Seen(
        val sizeBytes: Long,
        val modifiedAt: Long,
        /** When this exact size and time were first observed. */
        val since: Long,
    )

    private val seen = mutableMapOf<String, Seen>()
    private val failures = mutableMapOf<String, Int>()

    /**
     * One pass. Safe to call on a timer; holds no lock and keeps no file open.
     *
     * Not a loop of its own, so the caller decides how often to look and can stop between
     * passes. Photographs are sent oldest first, so a gallery fills in the order they were
     * taken rather than in whatever order the file system lists them.
     */
    suspend fun sweep(): SweepReport {
        val now = clock.now().toEpochMilliseconds()
        val handled = log.handled()
        val listing = folder.list()

        // A file that has gone stops being tracked, so a folder emptied between events does
        // not grow this map for the length of the process.
        val present = listing.mapTo(mutableSetOf()) { it.path }
        seen.keys.retainAll(present)
        failures.keys.retainAll(present)

        var sent = 0
        var waiting = 0
        var deferred = 0
        val refused = mutableListOf<RefusedPhotograph>()

        for (file in listing.sortedBy { it.modifiedAt }) {
            if (file.path in handled || !isDeliverable(file.path)) continue

            // Zero bytes is a file the camera has created and not yet written to. Not
            // "waiting on settling" — there is nothing there to settle — but counted as
            // waiting because that is what it is, and left alone so it can grow.
            if (file.sizeBytes == 0L) {
                waiting++
                continue
            }

            val previously = seen[file.path]
            if (previously == null ||
                previously.sizeBytes != file.sizeBytes ||
                previously.modifiedAt != file.modifiedAt
            ) {
                // Changed since the last look, so the clock starts again. A file written in
                // bursts resets on every burst, which is the intent.
                seen[file.path] = Seen(file.sizeBytes, file.modifiedAt, since = now)
                waiting++
                continue
            }

            if (now - previously.since < settleFor.inWholeMilliseconds) {
                waiting++
                continue
            }

            val bytes =
                try {
                    folder.read(file.path)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // Locked by the camera, or removed between the listing and the read.
                    // Neither is permanent, so it stays queued.
                    failures[file.path] = (failures[file.path] ?: 0) + 1
                    deferred++
                    continue
                }

            // Read after settling rather than during, but the file could still have been
            // rewritten in between. Sending a length that disagrees with what was measured
            // would be sending something never checked for stability.
            if (bytes.size.toLong() != file.sizeBytes) {
                seen[file.path] = Seen(bytes.size.toLong(), file.modifiedAt, since = now)
                waiting++
                continue
            }

            val outcome =
                try {
                    uploader.upload(
                        eventId = eventId,
                        sourceKey = sourceKey,
                        // The capture time, not the arrival time. A laptop that lost its
                        // network for ten minutes delivers a backlog belonging to slots that
                        // have since closed, and the server routes on this field.
                        capturedAt = file.modifiedAt,
                        fileName = file.path,
                        contentType = contentTypeOf(file.path),
                        bytes = bytes,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    UploadOutcome.Unavailable(failure.message ?: "the upload did not complete")
                }

            when (outcome) {
                is UploadOutcome.Stored -> {
                    log.record(file.path, delivered = true)
                    failures.remove(file.path)
                    sent++
                }

                is UploadOutcome.Refused -> {
                    // Recorded so it is never attempted again, and named so somebody knows.
                    log.record(file.path, delivered = false)
                    failures.remove(file.path)
                    refused += RefusedPhotograph(file.path, outcome.reason)
                }

                is UploadOutcome.Unavailable -> {
                    failures[file.path] = (failures[file.path] ?: 0) + 1
                    deferred++
                }
            }
        }

        return SweepReport(
            sent = sent,
            waiting = waiting,
            refused = refused,
            deferred = deferred,
            stuck = failures.filterValues { it >= attemptsBeforeStuck }.keys.sorted(),
        )
    }

    private companion object {
        /**
         * What a phone can open, and nothing else.
         *
         * Tethered capture usually writes a raw file beside every JPEG. Those are not sent:
         * an attendee's phone cannot display a CR3, and a 60MB file per frame would swamp
         * the event's connection for a photograph nobody could look at. The studio's own
         * copy of the raw stays where it was shot, which is where it was always going to be
         * edited from.
         */
        val DELIVERABLE = setOf("jpg", "jpeg", "png", "heic", "heif", "webp")

        fun extensionOf(path: String): String = path.substringAfterLast('.', "").lowercase()

        fun isDeliverable(path: String): Boolean {
            val name = path.substringAfterLast('/').substringAfterLast('\\')

            // Dot-files are the editor's and the file system's, never the camera's, and a
            // leading underscore is what several capture tools name their in-progress
            // writes.
            if (name.startsWith(".") || name.startsWith("_")) return false

            return extensionOf(name) in DELIVERABLE
        }

        fun contentTypeOf(path: String): String =
            when (extensionOf(path)) {
                "png" -> "image/png"
                "heic", "heif" -> "image/heic"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
    }
}
