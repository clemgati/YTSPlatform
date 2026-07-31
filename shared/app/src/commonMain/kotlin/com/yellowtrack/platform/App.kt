package com.yellowtrack.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yellowtrack.platform.app.AppShell
import com.yellowtrack.platform.app.rememberAppState
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.ServiceTemplateRepository
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionState
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

    val session by auth.session.collectAsStateWithLifecycle()

    YellowTrackTheme {
        when (session) {
            // Nothing is drawn until the stored session has been read. Showing the shell
            // and swapping it for a sign-in screen a frame later would flash the studio's
            // own data at whoever is holding the device, and showing sign-in first would
            // flash it at somebody who is already signed in.
            SessionState.Unknown -> Unit

            SessionState.SignedOut -> SignInRoute()

            is SessionState.SignedIn ->
                AppShell(
                    appState = rememberAppState(),
                )
        }
    }
}
