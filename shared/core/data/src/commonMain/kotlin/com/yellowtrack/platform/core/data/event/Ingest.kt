package com.yellowtrack.platform.core.data.event

/** A folder the studio picked, as the platform describes it. */
data class ChosenFolder(
    /** What the platform needs to reach it again. Not shown to anybody. */
    val path: String,
    /**
     * The last segment, which is what a studio recognises and what becomes the source key.
     *
     * A photographer names the folder after the camera writing into it, so this is already
     * the name they would have typed.
     */
    val name: String,
)

/**
 * The parts of watching a folder that only a real machine has.
 *
 * Three things, and they are together because they are the same seam: choosing a folder,
 * reading it, and remembering what has already been sent. A platform either has all three or
 * has none — a browser has no folder to watch and no disk to remember on.
 */
interface IngestPlatform {
    /**
     * False where the platform has no watchable folder at all.
     *
     * Said as a capability rather than discovered by calling and getting null, because the
     * screen has to decide whether to offer the button before anybody presses it. Offering a
     * control that can only fail is worse than not offering it.
     */
    val canWatchFolders: Boolean

    /** Null when the studio cancelled, or when this platform cannot ask. */
    suspend fun chooseFolder(): ChosenFolder?

    fun folderAt(path: String): WatchedFolder

    /**
     * The log for one folder, in one event, on one source.
     *
     * Keyed by all three: the same folder reused for a later event starts empty, and two
     * cameras writing into one folder — which happens — keep separate lists.
     */
    fun logFor(
        folderPath: String,
        eventId: String,
        sourceKey: String,
    ): UploadLog

    companion object {
        /**
         * For the platforms that cannot.
         *
         * Android, iOS and the browser have no tethered capture folder to watch. This is not
         * a gap to fill later — a phone is not what a camera is tethered to — so it says so
         * plainly rather than throwing somewhere deeper.
         */
        val Unavailable: IngestPlatform =
            object : IngestPlatform {
                override val canWatchFolders: Boolean = false

                override suspend fun chooseFolder(): ChosenFolder? = null

                override fun folderAt(path: String): WatchedFolder =
                    throw UnsupportedOperationException("this platform has no folder to watch")

                override fun logFor(
                    folderPath: String,
                    eventId: String,
                    sourceKey: String,
                ): UploadLog = throw UnsupportedOperationException("this platform has no folder to watch")
            }
    }
}

/**
 * What one watched folder has done so far, as the studio needs to read it.
 *
 * The counts are cumulative rather than per sweep. A sweep happens every two seconds and
 * usually does nothing, so a per-sweep "0 sent" is what a photographer would see almost
 * always — true, useless, and indistinguishable from ingest being broken.
 */
data class IngestStatus(
    val sourceKey: String,
    val folderName: String,
    /** Delivered since this watch started. */
    val sent: Int = 0,
    /** Seen but not yet settled. Normal during a burst. */
    val waiting: Int = 0,
    /** Failed for a reason that may pass, and still queued. */
    val deferred: Int = 0,
    /** Photographs the server will never take. Each one is a photograph nobody receives. */
    val refused: List<RefusedPhotograph> = emptyList(),
    /** Queued and no longer plausibly about to work. */
    val stuck: List<String> = emptyList(),
    /**
     * The last sweep threw, rather than reporting a failure.
     *
     * A folder unmounted, a disk gone read-only, a permission withdrawn. Carried because the
     * loop deliberately continues afterwards, and a loop that continues silently is
     * indistinguishable from one with nothing to do.
     */
    val lastSweepFailed: String? = null,
) {
    /** Something a studio should look at before the guests leave. */
    val needsAttention: Boolean
        get() = refused.isNotEmpty() || stuck.isNotEmpty() || lastSweepFailed != null
}
