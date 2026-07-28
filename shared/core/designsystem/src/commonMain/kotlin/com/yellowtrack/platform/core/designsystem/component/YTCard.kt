package com.yellowtrack.platform.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

@Composable
fun YTCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues =
        PaddingValues(
            YTTheme.spacing.medium,
        ),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = YTTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor = YTTheme.colors.surface,
                contentColor = YTTheme.colors.onSurface,
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 2.dp,
            ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}
