package com.yellowtrack.platform.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.app.components.CompactNavigationBar
import com.yellowtrack.platform.app.components.ExpandedSidebar
import com.yellowtrack.platform.core.designsystem.component.YTScaffold
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.clients.ClientDetailsRoute
import com.yellowtrack.platform.feature.clients.ClientsRoute
import com.yellowtrack.platform.feature.dashboard.DashboardRoute

@Composable
fun AppShell(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val useExpandedNavigation =
            maxWidth >= ExpandedNavigationBreakpoint

        if (useExpandedNavigation) {
            ExpandedAppShell(
                appState = appState,
            )
        } else {
            CompactAppShell(
                appState = appState,
            )
        }
    }
}

@Composable
private fun ExpandedAppShell(appState: AppState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = YTTheme.colors.background,
        contentColor = YTTheme.colors.onBackground,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            ExpandedSidebar(
                currentDestination = appState.currentDestination,
                onDestinationSelected = appState::navigateTopLevel,
            )
            CurrentDestination(
                appState,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CompactAppShell(appState: AppState) {
    YTScaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            CompactNavigationBar(
                currentDestination = appState.currentDestination,
                onDestinationSelected = appState::navigateTopLevel,
            )
        },
    ) { contentPadding ->
        CurrentDestination(
            appState,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

@Composable
private fun CurrentDestination(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    when (appState.currentDestination) {
        AppDestination.Dashboard ->
            DashboardRoute(
                modifier = modifier,
            )

        AppDestination.Clients ->
            ClientsDestination(
                appState = appState,
                modifier = modifier,
            )

        AppDestination.Sessions -> {}

        AppDestination.Studio -> {}

        AppDestination.Settings -> {}
    }
}

@Composable
private fun ClientsDestination(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    val selectedClientId = appState.selectedClientId

    if (selectedClientId == null) {
        ClientsRoute(
            onClientSelected = appState::openClient,
            modifier = modifier,
        )
    } else {
        ClientDetailsRoute(
            clientId = selectedClientId,
            onBack = appState::closeClientDetails,
            onScheduleSession = { clientId ->
                // YTP-014: open session scheduling for clientId.
            },
            onEditClient = { clientId ->
                // YTP-013C: open the client editor for clientId.
            },
            onArchiveClient = { clientId ->
                // Future: show archive confirmation for clientId.
            },
            modifier = modifier,
        )
    }
}

private val ExpandedNavigationBreakpoint = 840.dp
