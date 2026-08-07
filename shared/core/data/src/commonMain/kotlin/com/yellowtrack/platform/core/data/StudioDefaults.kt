package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.data.sync.SyncStatus
import com.yellowtrack.platform.core.data.sync.WriteFailed
import com.yellowtrack.platform.core.model.common.StudioId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.time.Instant

/**
 * Fills in the four default packages and the studio's own name, once the studio — not the
 * device — has been asked whether they are already there.
 *
 * Both of those used to decide by counting rows on this device, which is the wrong question.
 * An empty device is not an empty studio. A signed-in device that answers it locally invents
 * four templates and a profile at `version = 1` and pushes them at a server holding `version
 * = 42`, and every one of those is recorded as a conflict somebody is then asked to
 * adjudicate one at a time.
 *
 * Found in production, from the conflict rows themselves: the two sides were byte-identical
 * apart from the audit block, and the incoming `createdAt` was a quarter of a second before
 * the push. Nothing was in conflict. A row had been *invented* and then argued with.
 *
 * A browser tab did it every three minutes, because its database is in memory and therefore
 * empty that often. That part is the browser's fault and ADR 0012 already records it. **The
 * same thing happens once on any second laptop**, which is not.
 *
 * Extracted from `App`'s effect so the ordering can be tested. The rule is only interesting
 * as a sequence — *not before the first sync* — and a composable is a poor place to assert
 * that.
 */
suspend fun fillStudioDefaults(
    session: SessionState,
    status: Flow<SyncStatus>,
    serviceTemplates: ServiceTemplateRepository,
    profiles: StudioProfileRepository,
    studioId: StudioId,
    now: Instant,
) {
    when (session) {
        // Nothing, on both counts. There is no studio to seed for — a signed-out device has
        // only the placeholder id — and no screen behind the sign-in form to show templates
        // on anyway. Seeding here used to leave four packages under the placeholder studio,
        // which are visible in the database and belong to nobody.
        SessionState.SignedOut, SessionState.Unknown -> Unit

        is SessionState.SignedIn -> {
            // Suspends until the studio has answered. Staying empty is the right behaviour
            // when it cannot: a studio that is offline sees no defaults for now, where the
            // alternative is inventing rows that collide with its real ones the moment it
            // reconnects. Nothing is lost by waiting, and the wait ends at the first sync.
            // firstOrNull rather than first: a flow that ends without ever succeeding means
            // the studio never answered, which is the same as not having answered yet. first
            // would raise NoSuchElementException there, inside an effect, and take the screen
            // with it — for the one case this function exists to treat as "say nothing".
            if (status.firstOrNull { it is SyncStatus.Succeeded } == null) return

            // Both of these now write through the server, so both can fail on a connection
            // that dropped between the sync and here. Swallowed on purpose: defaults are not
            // work a studio typed, nobody is waiting on the result, and the next launch tries
            // again. An unhandled WriteFailed here would instead take down the effect that
            // draws the application.
            runCatching {
                serviceTemplates.seedDefaultsIfEmpty()
                profiles.adoptStudioName(
                    studioName = session.session.studioName,
                    studioId = studioId,
                    now = now,
                )
            }.onFailure { if (it !is WriteFailed) throw it }
        }
    }
}
