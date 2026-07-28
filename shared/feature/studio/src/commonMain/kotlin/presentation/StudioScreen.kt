package com.yellowtrack.platform.feature.studio.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun StudioScreen(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(YTTheme.spacing.extraLarge),
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.large,
            ),
    ) {
        YTBadge("Genesis")

        Text(
            text = "Clients",
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        YTCard {
            Text(
                "Manage your Studio",
                style = YTTheme.typography.bodyLarge,
            )
        }
    }
}
