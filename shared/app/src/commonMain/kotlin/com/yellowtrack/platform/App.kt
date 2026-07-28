package com.yellowtrack.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.yellowtrack.platform.app.AppShell
import com.yellowtrack.platform.app.rememberAppState
import com.yellowtrack.platform.core.data.ServiceTemplateRepository
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import org.koin.compose.koinInject

@Composable
fun App() {
    // The database is created on first use rather than during injection, because driver
    // creation suspends and wasm has no way to block. Seeding runs here, once, for the
    // same reason — there is no non-suspending startup hook that works on every platform.
    val serviceTemplates: ServiceTemplateRepository = koinInject()

    LaunchedEffect(serviceTemplates) {
        serviceTemplates.seedDefaultsIfEmpty()
    }

    YellowTrackTheme {
        AppShell(
            appState = rememberAppState(),
        )
    }
}
