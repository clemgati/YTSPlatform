package com.yellowtrack.platform.core.data.event

/**
 * A file in the watched folder, as the watcher can see it without opening it.
 *
 * Size and time together are the fingerprint [FolderWatch] uses to decide whether a camera
 * has finished writing. Neither alone is enough: a file can be rewritten to the same length,
 * and some cameras set the modification time up front.
 */
data class WatchedFile(
    /** Identifies the file within its folder, and is the key the upload log remembers. */
    val path: String,
    val sizeBytes: Long,
    /**
     * When the file was last written.
     *
     * Also what the watcher sends as the capture time, because for tethered capture the two
     * are the same moment to within the write itself.
     */
    val modifiedAt: Long,
)

/**
 * The folder tethered capture writes to.
 *
 * An interface rather than a path, so the decisions in [FolderWatch] — when a file has
 * settled, what to do with a refusal, what never to send twice — can be tested without a
 * disk. Those decisions are the whole of the watcher, and none of them need a real file to
 * ask.
 */
interface WatchedFolder {
    /** What is in the folder now. Not recursive: a capture folder is flat. */
    fun list(): List<WatchedFile>

    fun read(path: String): ByteArray
}

/** What became of one photograph. */
sealed interface UploadOutcome {
    data class Stored(
        val photoId: String,
    ) : UploadOutcome

    /**
     * The server will not take this file, and will not take it later either.
     *
     * Sending it again would loop forever, so the watcher stops — but it says which file and
     * why, because this is the outcome where a photograph is lost, and a count alone would
     * let a studio finish an event believing everything arrived.
     */
    data class Refused(
        val reason: String,
    ) : UploadOutcome

    /**
     * Not now — but this file is still good.
     *
     * A bucket outage, a flat network, an expired token. The file stays in the queue and the
     * next sweep tries again, because the alternative is deciding on a photographer's behalf
     * that a photograph was not worth keeping.
     */
    data class Unavailable(
        val reason: String,
    ) : UploadOutcome
}

/** Sends one photograph to the server. `core:network` holds the real one. */
interface PhotographUploader {
    suspend fun upload(
        eventId: String,
        sourceKey: String,
        capturedAt: Long,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): UploadOutcome
}

/**
 * What this machine has already dealt with, across restarts.
 *
 * Without it, a laptop that restarts mid-event re-reads the folder and sends every
 * photograph in it a second time — the studio's gallery doubles, and the attendee who
 * already had their photographs gets them again. In memory it survives nothing, which is why
 * the desktop implementation writes to disk.
 */
interface UploadLog {
    /** Paths that must not be sent again — whether they were delivered or refused. */
    suspend fun handled(): Set<String>

    /**
     * @param delivered false when the server refused the file. Recorded either way, because
     *   the question this log answers is "send it again?" and the answer is no in both
     *   cases. The flag is kept so a studio can be shown what was lost rather than only
     *   what worked.
     */
    suspend fun record(
        path: String,
        delivered: Boolean,
    )
}
