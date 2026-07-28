package com.yellowtrack.platform.core.designsystem.preview

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTIcon
import com.yellowtrack.platform.core.designsystem.component.YTIcons
import com.yellowtrack.platform.core.designsystem.component.YTListItem
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme

@Preview
@Composable
private fun YTListItemPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.background,
        ) {
            YTListItem(
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
                leadingContent = {
                    YTBadge(
                        text = "JS",
                    )
                },
                titleContent = {
                    Text(
                        text = "John Smith",
                        style = YTTheme.typography.titleMedium,
                        color = YTTheme.colors.onSurface,
                    )
                },
                subtitleContent = {
                    Text(
                        text = "12 sessions · Last session Jul 21",
                        style = YTTheme.typography.bodyMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    YTIcon(
                        icon = YTIcons.More,
                        contentDescription = "More options",
                    )
                },
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun YTListItemMinimalPreview() {
    YellowTrackTheme {
        Surface(
            color = YTTheme.colors.background,
        ) {
            YTListItem(
                modifier =
                    Modifier.padding(
                        YTTheme.spacing.large,
                    ),
                titleContent = {
                    Text(
                        text = "Simple list item",
                        style = YTTheme.typography.titleMedium,
                        color = YTTheme.colors.onSurface,
                    )
                },
            )
        }
    }
}
