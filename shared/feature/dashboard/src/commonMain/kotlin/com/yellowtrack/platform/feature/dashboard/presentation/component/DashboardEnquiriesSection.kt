package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTBadge
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardEnquiry

@Composable
internal fun DashboardEnquiriesSection(
    enquiries: List<DashboardEnquiry>,
    onMarkReplied: (LeadId) -> Unit,
    onAddEnquiry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YTSectionCard(
        title = "Awaiting your reply",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            if (enquiries.isEmpty()) {
                Text(
                    text = "Every enquiry has been answered.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                enquiries.forEachIndexed { index, enquiry ->
                    if (index > 0) {
                        HorizontalDivider(color = YTTheme.colors.outlineVariant)
                    }
                    EnquiryRow(enquiry = enquiry, onMarkReplied = onMarkReplied)
                }
            }

            TextButton(onClick = onAddEnquiry) {
                Text(
                    text = "Log an enquiry",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

@Composable
private fun EnquiryRow(
    enquiry: DashboardEnquiry,
    onMarkReplied: (LeadId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = enquiry.name,
                    style = YTTheme.typography.bodyLarge,
                    color = YTTheme.colors.onSurface,
                )

                if (enquiry.isUrgent) {
                    YTBadge(text = "OVERDUE")
                }
            }

            Text(
                text = "${enquiry.source} • waiting ${enquiry.waitingLabel}",
                style = YTTheme.typography.bodyMedium,
                color = if (enquiry.isUrgent) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
            )
        }

        // One tap, because the value of this record is the timestamp and anything more
        // ceremonious than a single button will not get used on a busy day.
        TextButton(onClick = { onMarkReplied(enquiry.id) }) {
            Text(
                text = "Replied",
                style = YTTheme.typography.labelLarge,
                color = YTTheme.colors.primary,
            )
        }
    }
}
