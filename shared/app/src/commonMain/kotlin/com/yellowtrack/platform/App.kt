package com.yellowtrack.platform

import androidx.compose.runtime.Composable
import com.yellowtrack.platform.app.AppShell
import com.yellowtrack.platform.app.rememberAppState
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme

@Composable
fun App() {
    YellowTrackTheme {
        AppShell(
            appState = rememberAppState(),
        )
    }
}
