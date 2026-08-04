package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardEnquiry

/**
 * Every enquiry, whatever became of it.
 *
 * The card above holds only what has never been answered, which is right for a list whose
 * job is to say what needs doing today. It also meant that replying to an enquiry, or
 * marking it won or lost, took it off the only screen in the application that showed leads
 * — so spam somebody answered, or the same enquiry logged twice, could never be found
 * again, let alone removed.
 *
 * This is the third time that shape has turned up: a payment leaves the money-owed list
 * when it settles an invoice, a quote leaves the proposals list when it is answered, and an
 * enquiry leaves this one when it is replied to. Each time the fix is the same — a list of
 * everything, next to the list of what is urgent.
 */
@Composable
internal fun DashboardAllEnquiriesSection(
    enquiries: List<DashboardEnquiry>,
    onRemoveEnquiry: (LeadId) -> Unit,
    onEditEnquiry: (DashboardEnquiry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (enquiries.isEmpty()) return

    YTSectionCard(
        title = "Every enquiry",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            enquiries.forEach { enquiry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = enquiry.name,
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.onSurface,
                        )
                        Text(
                            text = "${enquiry.statusLabel} · ${enquiry.source}",
                            style = YTTheme.typography.labelMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                    }

                    TextButton(onClick = { onEditEnquiry(enquiry) }) {
                        Text(
                            text = "Edit",
                            style = YTTheme.typography.labelMedium,
                            color = YTTheme.colors.primary,
                        )
                    }

                    TextButton(onClick = { onRemoveEnquiry(enquiry.id) }) {
                        Text(
                            text = "Remove",
                            style = YTTheme.typography.labelLarge,
                            color = YTTheme.colors.error,
                        )
                    }
                }
            }
        }
    }
}
