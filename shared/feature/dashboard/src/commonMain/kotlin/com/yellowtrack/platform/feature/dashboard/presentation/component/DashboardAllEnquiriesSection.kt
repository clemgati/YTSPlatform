package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.lead.LeadId
import com.yellowtrack.platform.feature.dashboard.presentation.model.DashboardEnquiry
import com.yellowtrack.platform.feature.dashboard.presentation.model.EnquiryOutcomesSummary

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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DashboardAllEnquiriesSection(
    enquiries: List<DashboardEnquiry>,
    onRemoveEnquiry: (LeadId) -> Unit,
    onEditEnquiry: (DashboardEnquiry) -> Unit,
    onConvertEnquiry: (LeadId) -> Unit,
    outcomes: EnquiryOutcomesSummary?,
    modifier: Modifier = Modifier,
) {
    if (enquiries.isEmpty()) return

    YTSectionCard(
        title = "Every enquiry",
        modifier = modifier,
    ) {
        // What became of them, above the list rather than below it. It is the question a
        // studio asks about this section, and the answer should not be at the bottom of two
        // hundred rows.
        outcomes?.let { summary ->
            Text(
                text = summary.headline,
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurface,
            )
            Text(
                text = summary.detail,
                style = YTTheme.typography.labelMedium,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
            enquiries.forEach { enquiry ->
                // The name on its own line, actions under it. Three buttons beside a name do
                // not fit a phone: adding "Make client" pushed "Priya & Tom" onto two lines
                // and its status onto four. The same fault the Gear rows had, and the same
                // remedy — a row is read before it is acted on.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
                ) {
                    Text(
                        text = enquiry.name,
                        style = YTTheme.typography.bodyMedium,
                        color = YTTheme.colors.onSurface,
                    )
                    Text(
                        text =
                            listOfNotNull(enquiry.statusLabel, enquiry.source, enquiry.convertedLabel)
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                        style = YTTheme.typography.labelMedium,
                        color = YTTheme.colors.onSurfaceVariant,
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
                        // Offered only where it would do something. A row that already
                        // produced a client says so in the line above instead.
                        if (enquiry.canConvert) {
                            TextButton(onClick = { onConvertEnquiry(enquiry.id) }) {
                                Text(
                                    text = "Make client",
                                    style = YTTheme.typography.labelMedium,
                                    color = YTTheme.colors.primary,
                                )
                            }
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
                                style = YTTheme.typography.labelMedium,
                                color = YTTheme.colors.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
