package com.yellowtrack.platform.core.data.sync

import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import kotlinx.coroutines.CancellationException

/**
 * Why a write did not happen, in words a studio should read.
 *
 * A class rather than a silent local save, which is the whole of ADR 0012 in one type. The
 * moment a write cannot happen is a worse moment to be told and a better day: a studio told
 * now can write it down or wait, where one told in five minutes has already moved on.
 */
sealed class WriteFailed(
    message: String,
) : Exception(message) {
    data object Offline : WriteFailed(
        "You are offline, so that could not be saved. Nothing has been lost — try again when you have a connection.",
    )

    data class Refused(
        val detail: String,
    ) : WriteFailed(detail)
}

/**
 * Sends one change to the server and waits for it.
 *
 * ADR 0012 decision 1. The server is the source of truth and a write succeeds when it has it,
 * which is what makes the conflict machinery unnecessary rather than merely improved:
 * **conflicts come from writing blind**, and a device that must reach the server to write
 * always holds current state.
 *
 * Deliberately over the sync transport rather than a new endpoint per entity. The push route
 * already upserts inside the studio's row level security scope, already knows every entity,
 * and is already tested against a real Postgres — twenty new endpoints would be twenty new
 * places for the tenanting to be got wrong.
 *
 * The version negotiation on the other side is harmless here and will go with the rest of it:
 * a write sent immediately carries the version this device just read, so it is always ahead
 * and always applies.
 */
class RemoteWriter(
    private val transport: SyncTransport,
) {
    /**
     * Sends, or throws [WriteFailed].
     *
     * Anything the transport throws is treated as "could not ask" rather than "was told no",
     * for the same reason sign-in does: telling a photographer their invoice was refused when
     * the venue has no signal is untrue and sends them looking in the wrong place.
     */
    suspend fun write(changes: SyncPushRequest) {
        val results =
            try {
                transport.push(changes)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                throw WriteFailed.Offline
            }

        // A rejection is the server refusing this specific row, which is a bug rather than a
        // network problem — reported as itself so it does not read as "try again later".
        results
            .firstOrNull { it.outcome == SyncPushOutcome.Rejected }
            ?.let { throw WriteFailed.Refused(it.detail ?: "The server would not accept that change.") }

        // Silence is not success. An empty answer to a request that carried a row means the
        // server never spoke about it, and writing the cache anyway would show the studio a
        // saved record that exists nowhere else.
        if (results.isEmpty()) throw WriteFailed.Offline
    }
}
