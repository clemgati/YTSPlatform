package com.yellowtrack.platform.core.data.event

import com.yellowtrack.platform.core.common.time.AppClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Watching folders for as long as their stations are open.
 *
 * The piece between [FolderWatch], which decides what to send, and the screen, which shows
 * what happened. Its own job is small and entirely about the loop: run a sweep every couple
 * of seconds, keep going when one fails, and never run two on the same folder.
 *
 * ## Why one loop per source is a correctness rule
 *
 * Two loops on one folder both sweep, both find the same settled file unhandled — the log is
 * only written *after* an upload succeeds — and both send it. The result is every photograph
 * in the gallery twice, and every attendee mailed twice. So starting a watch on a source
 * already being watched does nothing rather than starting a second.
 *
 * ## Why a failing sweep does not stop it
 *
 * A folder can be unmounted, go read-only, or have a permission withdrawn in the middle of an
 * event. Stopping would mean ingest silently ending while the screen still said a station was
 * open. The loop continues and the failure is carried on the status, because a loop that
 * continues quietly is indistinguishable from one with nothing to do — which is the failure
 * this project keeps finding.
 */
class IngestService(
    private val platform: IngestPlatform,
    private val uploader: PhotographUploader,
    private val scope: CoroutineScope,
    private val clock: AppClock = AppClock.System,
    /**
     * How often to look.
     *
     * Two seconds against "immediate review": a guest waits at the screen for their headshot,
     * and the wait they notice is this plus the settle plus the upload.
     */
    private val every: Duration = 2.seconds,
) {
    private val watches = mutableMapOf<String, Job>()
    private val state = MutableStateFlow<Map<String, IngestStatus>>(emptyMap())

    /** Keyed by source, which is what a station is bound to. */
    val status: StateFlow<Map<String, IngestStatus>> = state.asStateFlow()

    /**
     * Begins watching [folder] for [sourceKey], unless it already is.
     *
     * Returns false when a watch was already running, so the screen can say "already
     * watching" rather than appearing to have done something.
     */
    fun watch(
        eventId: String,
        sourceKey: String,
        folder: ChosenFolder,
    ): Boolean {
        // Checked against a live job rather than against the status map, because a completed
        // or cancelled job leaves its status behind on purpose — the studio still wants to
        // read what a finished station did.
        if (watches[sourceKey]?.isActive == true) return false

        state.update { it + (sourceKey to IngestStatus(sourceKey, folder.name)) }

        watches[sourceKey] =
            scope.launch {
                val watch =
                    FolderWatch(
                        folder = platform.folderAt(folder.path),
                        uploader = uploader,
                        log = platform.logFor(folder.path, eventId, sourceKey),
                        eventId = eventId,
                        sourceKey = sourceKey,
                        clock = clock,
                    )

                while (isActive) {
                    try {
                        record(sourceKey, watch.sweep())
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Throwable) {
                        state.update { current ->
                            val existing = current[sourceKey] ?: return@update current
                            current +
                                (
                                    sourceKey to
                                        existing.copy(
                                            lastSweepFailed = failure.message ?: "the folder could not be read",
                                        )
                                )
                        }
                    }

                    delay(every)
                }
            }

        return true
    }

    /**
     * Stops watching, leaving the status behind.
     *
     * A photographer closing a station still wants to see that it sent 214 photographs and
     * that two were refused. Clearing it on stop would delete the only record of the refusals
     * at the exact moment somebody went looking for them.
     */
    fun stop(sourceKey: String) {
        watches.remove(sourceKey)?.cancel()
    }

    fun stopAll() {
        watches.values.forEach { it.cancel() }
        watches.clear()
    }

    fun isWatching(sourceKey: String): Boolean = watches[sourceKey]?.isActive == true

    private fun record(
        sourceKey: String,
        report: SweepReport,
    ) {
        state.update { current ->
            val existing = current[sourceKey] ?: return@update current

            current +
                (
                    sourceKey to
                        existing.copy(
                            // Cumulative. A sweep's own count is almost always zero, which
                            // tells a studio nothing and looks like a stall.
                            sent = existing.sent + report.sent,
                            waiting = report.waiting,
                            deferred = report.deferred,
                            // Also cumulative, and never dropped: a refusal is a photograph
                            // somebody will not receive, and it stops being in any later
                            // report because the file is never offered again.
                            refused = existing.refused + report.refused,
                            stuck = report.stuck,
                            // Cleared by a sweep that worked, so a transient unmount does not
                            // leave a warning up for the rest of the day.
                            lastSweepFailed = null,
                        )
                )
        }
    }
}
