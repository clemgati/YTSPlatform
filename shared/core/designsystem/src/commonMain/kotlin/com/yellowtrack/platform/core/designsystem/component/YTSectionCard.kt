package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme

@Composable
fun YTSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    YTCard(
        modifier = modifier,
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.medium,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        YTTheme.spacing.medium,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = YTTheme.typography.titleLarge,
                    color = YTTheme.colors.onSurface,
                )

                actions()
            }

            content()
        }
    }
}

@Preview
@Composable
private fun YTSectionCardPreview() {
    YellowTrackTheme {
        YTSectionCard(
            title = "Today's Sessions",
        ) {
            Text("Preview Content")
        }
    }
}
