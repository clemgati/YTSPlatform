package com.yellowtrack.platform.feature.clients.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientDetailsModel

@Composable
internal fun ClientDetailsHeader(
    client: ClientDetailsModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.medium,
            ),
    ) {
        YTBadge(
            text = client.initials,
        )

        Text(
            text = client.displayName,
            style = YTTheme.typography.headlineLarge,
            color = YTTheme.colors.onBackground,
        )

        if (client.tags.isNotEmpty()) {
            Text(
                text = client.tags.joinToString(" • "),
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}
