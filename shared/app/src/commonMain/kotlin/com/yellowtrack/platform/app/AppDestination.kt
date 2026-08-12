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

    Ledger(
        route = "ledger",
        label = "Ledger",
        icon = YTIcons.Ledger,
    ),

    Events(
        route = "events",
        label = "Events",
        icon = YTIcons.Gallery,
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
    ;

    companion object {
        /**
         * What a phone shows without being asked.
         *
         * Four, and not because four is tidy. All seven fit in the bar and every label was
         * ellipsised to fit them: on a 320dp phone the destinations read "Das…", "Clie…",
         * "Ses…", "Led…", "Eve…", "Stu…", "Sett…", and the studio that asked for this could
         * not find Events because "Eve…" is not a word. Fewer tabs, whole words.
         *
         * These four are the ones a studio touches on a shoot day. Everything else is a place
         * it goes deliberately, which is what [overflow] is for.
         */
        val primary = listOf(Dashboard, Clients, Sessions, Ledger)

        /**
         * The rest, behind More.
         *
         * Derived rather than listed, so a destination added later appears there instead of
         * silently appearing nowhere. Being under More is a worse fate than being on the bar;
         * being unreachable is worse than both.
         */
        val overflow = entries.filterNot { it in primary }
    }
}
