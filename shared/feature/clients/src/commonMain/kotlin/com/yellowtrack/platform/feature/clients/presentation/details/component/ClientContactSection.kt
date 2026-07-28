package com.yellowtrack.platform.feature.clients.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientContact

@Composable
internal fun ClientContactSection(
    contact: ClientContact,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Contact",
        modifier = modifier,
    ) {
        DetailRow(
            label = "Phone",
            value = contact.phone,
        )

        DetailRow(
            label = "Email",
            value = contact.email,
        )

        DetailRow(
            label = "Instagram",
            value = contact.instagram,
        )

        DetailRow(
            label = "Company",
            value = contact.company,
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String?,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(
                YTTheme.spacing.extraSmall,
            ),
    ) {
        Text(
            text = label,
            style = YTTheme.typography.labelMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )

        Text(
            text = value ?: "—",
            style = YTTheme.typography.bodyLarge,
            color = YTTheme.colors.onSurface,
        )
    }
}
