package com.yellowtrack.platform.feature.dashboard.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
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
    onConvertEnquiry: (LeadId, Boolean) -> Unit,
    outcomes: EnquiryOutcomesSummary?,
    modifier: Modifier = Modifier,
) {
    if (enquiries.isEmpty()) return

    var converting by remember { mutableStateOf<DashboardEnquiry?>(null) }

    converting?.let { enquiry ->
        ConvertEnquiryDialog(
            enquiry = enquiry,
            onDismiss = { converting = null },
            onConfirm = { openBooking ->
                onConvertEnquiry(enquiry.id, openBooking)
                converting = null
            },
        )
    }

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
                            TextButton(onClick = { converting = enquiry }) {
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

/**
 * What converting will actually do, before it does it.
 *
 * The booking is offered rather than assumed. An enquiry that is won is usually work, but not
 * always yet — somebody may have agreed in principle with no date and nothing to price — and a
 * project on the Ledger that nobody meant to open is harder to notice than a second press.
 *
 * Ticked by default, because the studio pressed this on an enquiry it has won.
 */
@Composable
private fun ConvertEnquiryDialog(
    enquiry: DashboardEnquiry,
    onDismiss: () -> Unit,
    onConfirm: (openBooking: Boolean) -> Unit,
) {
    var openBooking by remember { mutableStateOf(true) }

    YTFormDialog(
        title = "Make ${enquiry.name} a client",
        confirmLabel = "Make client",
        onConfirm = { onConfirm(openBooking) },
        onDismiss = onDismiss,
    ) {
        Text(
            text =
                "Their name and contact details come across from the enquiry, and it is marked " +
                    "won.",
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        ) {
            Checkbox(checked = openBooking, onCheckedChange = { openBooking = it })
            Text(
                text = "Open a booking for it too",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurface,
            )
        }

        Text(
            text =
                if (openBooking) {
                    "The booking opens as an enquiry, not as booked — a date is held once a " +
                        "contract is signed and its retainer paid."
                } else {
                    "You can open one later from the client's own page."
                },
            style = YTTheme.typography.labelMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )
    }
}
