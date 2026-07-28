package com.yellowtrack.platform.app

import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.yellowtrack.platform.core.designsystem.component.YTIcons

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Dashboard(
        route = "dashboard",
        label = "Dashboard",
        icon = YTIcons.Dashboard,
    ),

    Clients(
        route = "clients",
        label = "Clients",
        icon = YTIcons.Clients,
    ),

    Sessions(
        route = "sessions",
        label = "Sessions",
        icon = YTIcons.Sessions,
    ),

    Studio(
        route = "studio",
        label = "Studio",
        icon = YTIcons.Studio,
    ),

    Settings(
        route = "settings",
        label = "Settings",
        icon = YTIcons.Settings,
    ),
}
