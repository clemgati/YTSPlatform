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
import com.yellowtrack.platform.feature.ledger.LedgerRoute
import com.yellowtrack.platform.feature.sessions.SessionsRoute
import com.yellowtrack.platform.feature.settings.presentation.SettingsScreen
import com.yellowtrack.platform.feature.studio.StudioRoute

@Composable
fun AppShell(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val useExpandedNavigation = maxWidth >= ExpandedNavigationBreakpoint

        if (useExpandedNavigation) {
            ExpandedAppShell(appState = appState)
        } else {
            CompactAppShell(appState = appState)
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
        Row(modifier = Modifier.fillMaxSize()) {
            ExpandedSidebar(
                currentDestination = appState.currentDestination,
                onDestinationSelected = appState::navigateTopLevel,
            )
            CurrentRoute(
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
        CurrentRoute(
            appState,
            modifier = Modifier.padding(contentPadding),
        )
    }
}

/**
 * Renders the top of the back stack.
 *
 * Exhaustive over [AppRoute], so adding a destination is a compile error here until it is
 * wired up — which is how Sessions, Studio, and Settings previously ended up as silently
 * empty branches.
 */
@Composable
private fun CurrentRoute(
    appState: AppState,
    modifier: Modifier = Modifier,
) {
    when (val route = appState.currentRoute) {
        AppRoute.Dashboard -> DashboardRoute(modifier = modifier)

        AppRoute.Clients ->
            ClientsRoute(
                onClientSelected = appState::openClient,
                modifier = modifier,
            )

        is AppRoute.ClientDetails ->
            ClientDetailsRoute(
                clientId = route.clientId,
                onBack = appState::navigateBack,
                onScheduleSession = { appState.navigateTopLevel(AppDestination.Sessions) },
                // Editing is the feature's own business and stays inside it, like every
                // other form: the app module has no reason to learn about "edit client".
                modifier = modifier,
            )

        AppRoute.Sessions ->
            // Selecting a session opens its editor, which the feature owns. There is no
            // separate detail route for the app module to push.
            SessionsRoute(modifier = modifier)

        AppRoute.Ledger -> LedgerRoute(modifier = modifier)

        AppRoute.Studio -> StudioRoute(modifier = modifier)

        AppRoute.Settings -> SettingsScreen(modifier = modifier)
    }
}

private val ExpandedNavigationBreakpoint = 840.dp
