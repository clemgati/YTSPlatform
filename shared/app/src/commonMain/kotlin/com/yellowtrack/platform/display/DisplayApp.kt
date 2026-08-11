package com.yellowtrack.platform.display

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.app.AppInfo
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.auth.SignInRoute
import com.yellowtrack.platform.feature.display.DisplayRoute
import org.koin.compose.koinInject

/**
 * Sign in, then be a sign.
 *
 * Deliberately smaller than the studio application's root. There is no navigation, because
 * there is nowhere else to go; no synchroniser, because this device produces nothing to
 * reconcile and holds no local copy of the studio's records; and no studio defaults, because
 * it never writes anything. It reads two things from the server and draws one of them.
 *
 * That is not tidiness. Everything left out is something that could fail on a tablet nobody
 * is looking at, and the failure would be a blank screen on a table.
 *
 * Shared between the Android and iOS companions. The two differ only in what they can do
 * about an unattended device — see [onDisplayingChanged], which each host answers with what
 * its platform actually offers rather than with a promise it cannot keep.
 */
@Composable
fun DisplayApp(
    /**
     * Called with true while a code is on the table.
     *
     * Android pins the screen and swallows Back. iOS can do neither — Guided Access is
     * started by a person holding the device and there is no API to ask for it — so there it
     * does nothing, and the password is the whole of the lock. Stated here rather than
     * quietly differing.
     */
    onDisplayingChanged: (Boolean) -> Unit = {},
) {
    val auth: AuthRepository = koinInject()
    val clock: AppClock = koinInject()

    LaunchedEffect(auth) {
        auth.restore(clock.now().toEpochMilliseconds())
    }

    val session by auth.session.collectAsStateWithLifecycle()

    YellowTrackTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = YTTheme.colors.background) {
            when (session) {
                // Nothing until the stored session has been read. A device switched on at
                // the start of an event should come up already signed in and showing the
                // list, not flash a password form at whoever is setting up the table.
                SessionState.Unknown -> Unit

                SessionState.SignedOut -> {
                    // Not displaying anything, so nothing should be pinned. Signing out is
                    // the one way to reach this screen from the other, and leaving the
                    // device pinned would trap the studio on a sign-in form.
                    LaunchedEffect(Unit) { onDisplayingChanged(false) }

                    SignInRoute(version = AppInfo.VERSION)
                }

                is SessionState.SignedIn -> DisplayRoute(onDisplayingChanged = onDisplayingChanged)
            }
        }
    }
}
