package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun YTDetailSection(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.medium,
            ),
    ) {
        if (showDivider) {
            HorizontalDivider()
        }

        Text(
            text = title,
            style = YTTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = YTTheme.colors.onBackground,
        )

        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.small,
                ),
            modifier =
                Modifier.padding(
                    bottom = YTTheme.spacing.medium,
                ),
        ) {
            content()
        }
    }
}
