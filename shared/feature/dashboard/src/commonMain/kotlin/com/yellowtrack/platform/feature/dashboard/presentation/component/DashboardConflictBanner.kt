package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * Tells the studio that synchronisation threw something away.
 *
 * ADR 0008 accepted last-write-wins only on the condition that the discarded version stays
 * recoverable by whoever wrote it. Settings holds the recovery; this holds the *telling*,
 * which is the half that decides whether the condition is really met. A conflict visible
 * only to a photographer who happens to open Settings is a conflict nobody is shown.
 *
 * A count and a direction, not the detail. What belongs on a dashboard is the fact that
 * something needs attention; unpicking which field was lost is a job for the screen with
 * room to do it properly.
 *
 * Deliberately not dismissible from here. The only way to clear it is to go and look at
 * what was lost, because a banner that can be waved away is a banner that gets waved away.
 */
@Composable
internal fun DashboardConflictBanner(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(YTTheme.spacing.medium))
                .background(YTTheme.colors.errorContainer)
                .padding(YTTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
    ) {
        Text(
            text =
                if (count == 1) {
                    "1 change was overwritten"
                } else {
                    "$count changes were overwritten"
                },
            style = YTTheme.typography.titleMedium,
            color = YTTheme.colors.onErrorContainer,
        )

        Text(
            text =
                if (count == 1) {
                    "You edited something on two devices at once. The version that was set " +
                        "aside is in Settings, so nothing is lost until you say so."
                } else {
                    "You edited these on two devices at once. The versions that were set " +
                        "aside are in Settings, so nothing is lost until you say so."
                },
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onErrorContainer,
        )
    }
}
