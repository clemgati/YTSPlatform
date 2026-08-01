package com.yellowtrack.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.app.AppShell
import com.yellowtrack.platform.app.rememberAppState
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ServiceTemplateRepository
import com.yellowtrack.platform.core.data.StudioContext
import com.yellowtrack.platform.core.data.StudioProfileRepository
import com.yellowtrack.platform.core.data.adoptStudioName
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
import com.yellowtrack.platform.core.data.sync.Synchroniser
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.auth.SignInRoute
import org.koin.compose.koinInject

@Composable
fun App() {
    // The database is created on first use rather than during injection, because driver
    // creation suspends and wasm has no way to block. Seeding runs here, once, for the
    // same reason — there is no non-suspending startup hook that works on every platform.
    val serviceTemplates: ServiceTemplateRepository = koinInject()
    val auth: AuthRepository = koinInject()
    val clock: AppClock = koinInject()

    LaunchedEffect(serviceTemplates) {
        serviceTemplates.seedDefaultsIfEmpty()
    }

    LaunchedEffect(auth) {
        auth.restore(clock.now().toEpochMilliseconds())
    }

    val synchroniser: Synchroniser = koinInject()
    val session by auth.session.collectAsStateWithLifecycle()

    // The studio named itself when it signed up; nothing carried that across to the profile
    // every document is built from. Keyed on the session so a second device fills itself in
    // too — the profile does not synchronise yet.
    val profiles: StudioProfileRepository = koinInject()
    val studioContext: StudioContext = koinInject()

    LaunchedEffect(session) {
        (session as? SessionState.SignedIn)?.let { signedIn ->
            profiles.adoptStudioName(
                studioName = signedIn.session.studioName,
                studioId = studioContext.studioId,
                now = clock.now(),
            )
        }
    }

    // Started once, on the first session. Reconciling is the whole point of having signed
    // in, and leaving it to a button would mean a device only ever held what its own user
    // typed into it.
    LaunchedEffect(session is SessionState.SignedIn) {
        if (session is SessionState.SignedIn) synchroniser.startPeriodicSync()
    }

    YellowTrackTheme {
        when (session) {
            // Nothing is drawn until the stored session has been read. Showing the shell
            // and swapping it for a sign-in screen a frame later would flash the studio's
            // own data at whoever is holding the device, and showing sign-in first would
            // flash it at somebody who is already signed in.
            SessionState.Unknown -> Unit

            // Wrapped, because screens in this application paint no background of their
            // own — AppShell does — and this one deliberately sits outside it. Without
            // this the heading is white on the default light surface and the warning
            // underneath is illegible. Found by running it; the render test had supplied
            // the Surface the application did not.
            SessionState.SignedOut ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = YTTheme.colors.background,
                ) {
                    SignInRoute()
                }

            is SessionState.SignedIn ->
                AppShell(
                    appState = rememberAppState(),
                )
        }
    }
}
