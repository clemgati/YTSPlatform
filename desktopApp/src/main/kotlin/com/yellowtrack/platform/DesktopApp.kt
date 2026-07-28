package com.yellowtrack.platform

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.yellowtrack.platform.core.di.initKoin

fun main() {
    // Must run before the first composition: `App()` injects its repositories.
    initKoin()

    application {
        // Compose Desktop defaults to 800x600, which is below the 840dp breakpoint at
        // which the shell switches to its sidebar. Without an explicit size the desktop
        // application would always open in the compact phone layout, and the expanded
        // layout would only ever be seen by someone who happened to resize the window.
        val windowState = rememberWindowState(size = DpSize(1_280.dp, 860.dp))

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Yellow Track Studio",
        ) {
            App()
        }
    }
}
