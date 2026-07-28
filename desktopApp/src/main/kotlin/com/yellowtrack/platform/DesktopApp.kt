package com.yellowtrack.platform

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() =
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "yellow-track-platform-bootstrap",
        ) {
            App()
        }
    }
