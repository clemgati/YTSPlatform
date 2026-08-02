package com.yellowtrack.platform.core.data.auth

import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.model.common.StudioId

/**
 * The studio whose data the application is working with: the signed-in one.
 *
 * `StudioContext` has said since 0.3.0 that "when accounts arrive, this becomes the
 * signed-in user's studio and the same queries keep working". Accounts have arrived, and
 * until this existed every row was still being written under the placeholder constant
 * while the server knew the device as something else — so a push would have been refused
 * as another studio's row, which is the right refusal to the wrong question.
 *
 * Read from the session on every access rather than captured once. Signing out and back in
 * as somebody else has to change what the repositories see, and a captured value would
 * quietly serve the previous studio's data to the new one.
 */
class SessionStudioContext(
    private val auth: AuthRepository,
) : StudioContext {
    override val studioId: StudioId
        get() =
            when (val state = auth.session.value) {
                is SessionState.SignedIn -> StudioId(state.session.studioId)
                // Signed out, or not yet restored. Nothing reads a repository here — the
                // shell shows sign-in instead — so this is a floor rather than a path, and
                // it is the placeholder rather than a throw because a flow still being
                // collected through the transition should go quiet, not crash.
                else -> LocalStudioContext.LOCAL_STUDIO_ID
            }
}
