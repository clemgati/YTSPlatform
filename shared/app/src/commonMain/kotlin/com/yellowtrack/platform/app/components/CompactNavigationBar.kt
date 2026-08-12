package com.yellowtrack.platform.app.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.yellowtrack.platform.app.AppDestination
import com.yellowtrack.platform.core.designsystem.component.YTIcon
import com.yellowtrack.platform.core.designsystem.component.YTIcons
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * The phone's navigation: four destinations and a way to the rest.
 *
 * It used to be all seven. They fitted, in the sense that nothing was clipped, and every
 * label was cut to make them fit — on a 320dp phone the bar read "Das…", "Clie…", "Ses…",
 * "Led…", "Eve…", "Stu…", "Sett…". A studio looking for Events did not find it, which is a
 * fair reading of a tab labelled "Eve…", and reported it as missing rather than as truncated.
 *
 * So the bar carries what a shoot day needs and More carries the rest. The point is not the
 * count; it is that a label a person cannot read is not navigation.
 */
@Composable
fun CompactNavigationBar(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    var showingMore by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        NavigationBar(
            containerColor = YTTheme.colors.surface,
            contentColor = YTTheme.colors.onSurface,
        ) {
            AppDestination.primary.forEach { destination ->
                NavigationBarItem(
                    selected = destination == currentDestination,
                    onClick = { onDestinationSelected(destination) },
                    colors = itemColours(),
                    icon = { YTIcon(icon = destination.icon, contentDescription = destination.label) },
                    label = { Label(destination.label) },
                )
            }

            NavigationBarItem(
                // Selected while the studio is on one of the destinations behind it, so the
                // bar never claims nothing is open when something is.
                selected = currentDestination in AppDestination.overflow,
                onClick = { showingMore = true },
                colors = itemColours(),
                icon = { YTIcon(icon = YTIcons.More, contentDescription = "More") },
                label = { Label("More") },
            )
        }

        // Anchored to the end of the bar, where the More tab is. A menu opened from the
        // bottom of the screen has nowhere below it to go, so it opens upward.
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(
                expanded = showingMore,
                onDismissRequest = { showingMore = false },
            ) {
                AppDestination.overflow.forEach { destination ->
                    DropdownMenuItem(
                        text = { Text(text = destination.label, style = YTTheme.typography.bodyLarge) },
                        leadingIcon = {
                            YTIcon(icon = destination.icon, contentDescription = null)
                        },
                        onClick = {
                            showingMore = false
                            onDestinationSelected(destination)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = YTTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun itemColours() =
    NavigationBarItemDefaults.colors(
        selectedIconColor = YTTheme.colors.onPrimaryContainer,
        selectedTextColor = YTTheme.colors.primary,
        indicatorColor = YTTheme.colors.primaryContainer,
        unselectedIconColor = YTTheme.colors.onSurfaceVariant,
        unselectedTextColor = YTTheme.colors.onSurfaceVariant,
    )
