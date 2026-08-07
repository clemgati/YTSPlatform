package com.yellowtrack.platform.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.app.AppDestination
import com.yellowtrack.platform.app.AppInfo
import com.yellowtrack.platform.core.designsystem.component.YTIcon
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun ExpandedSidebar(
    currentDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .width(SidebarWidth)
                .fillMaxHeight()
                .background(YTTheme.colors.surface)
                .padding(YTTheme.spacing.large),
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.small,
            ),
    ) {
        Text(
            text = "Yellow Track",
            style = YTTheme.typography.titleLarge,
            color = YTTheme.colors.onSurface,
        )

        Spacer(
            modifier =
                Modifier.padding(
                    top = YTTheme.spacing.medium,
                ),
        )

        // Scrolls, and takes whatever height is left. A phone held sideways gives this
        // column about four hundred points, which is not enough for the destinations plus
        // the heading plus the version — so the last entries were simply cut off, and
        // Settings is the last entry. Nothing indicated there was more.
        //
        // The weight is on the scrolling area rather than a spacer so the version stays
        // pinned to the bottom instead of scrolling away with the list.
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.small,
                ),
        ) {
            AppDestination.entries.forEach { destination ->
                SidebarDestination(
                    destination = destination,
                    selected = destination == currentDestination,
                    onClick = {
                        onDestinationSelected(destination)
                    },
                )
            }
        }

        Text(
            text = AppInfo.VERSION,
            style = YTTheme.typography.labelMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun SidebarDestination(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor =
        if (selected) {
            YTTheme.colors.primary
        } else {
            YTTheme.colors.onSurface
        }

    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = YTTheme.shapes.medium,
        colors =
            ButtonDefaults.textButtonColors(
                contentColor = contentColor,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = YTTheme.spacing.small,
                        vertical = YTTheme.spacing.extraSmall,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.medium,
                ),
        ) {
            YTIcon(
                icon = destination.icon,
                contentDescription = destination.label,
                tint =
                    if (selected) {
                        YTTheme.colors.primary
                    } else {
                        YTTheme.colors.onSurfaceVariant
                    },
            )

            Text(
                text = destination.label,
                style = YTTheme.typography.labelLarge,
                color = contentColor,
            )
        }
    }
}

private val SidebarWidth = 240.dp
