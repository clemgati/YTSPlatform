package com.yellowtrack.platform.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.navigation.BackStack

/**
 * Top-level navigation state.
 *
 * Adapts the platform-independent [BackStack] into observable Compose state. Per ADR 0005
 * there is a single stack, and selecting a top-level destination resets it.
 */
class AppState internal constructor() {
    private var backStack by mutableStateOf(BackStack.of<AppRoute>(AppRoute.Dashboard))

    val currentRoute: AppRoute get() = backStack.current

    val currentDestination: AppDestination get() = currentRoute.destination

    val canNavigateBack: Boolean get() = backStack.canNavigateBack

    fun navigateTopLevel(destination: AppDestination) {
        backStack =
            if (destination == currentDestination) {
                // Tapping the current tab returns to its root rather than doing nothing,
                // which is the behaviour people expect from a bottom navigation bar.
                backStack.popToRoot()
            } else {
                backStack.resetTo(destination.rootRoute)
            }
    }

    fun openClient(clientId: ClientId) {
        backStack = backStack.push(AppRoute.ClientDetails(clientId))
    }

    fun openSession(sessionId: SessionId) {
        backStack = backStack.push(AppRoute.SessionDetails(sessionId))
    }

    fun navigateBack() {
        backStack = backStack.pop()
    }
}

@Composable
fun rememberAppState(): AppState = remember { AppState() }
