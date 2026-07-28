package com.yellowtrack.platform.app.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import com.yellowtrack.platform.app.AppDestination
import com.yellowtrack.platform.core.designsystem.component.YTIcon
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun CompactNavigationBar(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar(
        containerColor = YTTheme.colors.surface,
        contentColor = YTTheme.colors.onSurface,
    ) {
        AppDestination.entries.forEach { destination ->
            val selected = destination == currentDestination

            NavigationBarItem(
                selected = selected,
                onClick = {
                    onDestinationSelected(destination)
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = YTTheme.colors.onPrimaryContainer,
                        selectedTextColor = YTTheme.colors.primary,
                        indicatorColor = YTTheme.colors.primaryContainer,
                        unselectedIconColor = YTTheme.colors.onSurfaceVariant,
                        unselectedTextColor = YTTheme.colors.onSurfaceVariant,
                    ),
                icon = {
                    YTIcon(
                        icon = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = YTTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}
