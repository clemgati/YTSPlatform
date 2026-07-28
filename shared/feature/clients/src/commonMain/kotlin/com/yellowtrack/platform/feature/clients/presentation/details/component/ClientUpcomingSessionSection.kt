package com.yellowtrack.platform.feature.clients.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientUpcomingSession

@Composable
internal fun ClientUpcomingSessionSection(
    upcomingSession: ClientUpcomingSession?,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Upcoming Session",
        modifier = modifier,
    ) {
        if (upcomingSession == null) {
            Text(
                text = "No upcoming sessions scheduled.",
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurfaceVariant,
            )
        } else {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        YTTheme.spacing.extraSmall,
                    ),
            ) {
                Text(
                    text = upcomingSession.title,
                    style = YTTheme.typography.titleMedium,
                    color = YTTheme.colors.onSurface,
                )

                Text(
                    text = upcomingSession.date,
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                Text(
                    text = upcomingSession.time,
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                upcomingSession.location?.let { location ->
                    Text(
                        text = location,
                        style = YTTheme.typography.bodyMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
