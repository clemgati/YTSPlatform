package com.yellowtrack.platform.feature.clients.presentation.details.component

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
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.clients.presentation.details.model.BookingSummary

/**
 * Every job on the account, and where each one stands.
 *
 * A booking is the unit everything else hangs from — a contract, a set of invoices, and
 * the days in the diary all point at one — so a client whose bookings are invisible is a
 * client whose money and calendar cannot be reached from their own page.
 */
@Composable
internal fun ClientBookingsSection(
    bookings: List<BookingSummary>,
    onEditBooking: (BookingSummary) -> Unit,
    onAddBooking: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Bookings",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium)) {
            if (bookings.isEmpty()) {
                Text(
                    text = "No bookings yet. A quote, a contract, and an invoice all attach to one.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            } else {
                bookings.forEachIndexed { index, booking ->
                    if (index > 0) HorizontalDivider(color = YTTheme.colors.outlineVariant)
                    BookingRow(booking, onEditBooking)
                }
            }

            TextButton(onClick = onAddBooking) {
                Text(
                    text = "Open a booking",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}

@Composable
private fun BookingRow(
    booking: BookingSummary,
    onEdit: (BookingSummary) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = booking.name,
                style = YTTheme.typography.bodyLarge,
                color = YTTheme.colors.onSurface,
            )

            booking.value?.let { value ->
                Text(
                    text = value,
                    style = YTTheme.typography.titleSmall,
                    color = YTTheme.colors.onSurface,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    listOfNotNull(
                        booking.serviceLine,
                        booking.status.name,
                        booking.bookedLabel,
                    ).joinToString(" • "),
                modifier = Modifier.weight(1f),
                style = YTTheme.typography.bodyMedium,
                color =
                    if (booking.isHeld) {
                        YTTheme.colors.onSurface
                    } else {
                        YTTheme.colors.onSurfaceVariant
                    },
            )

            TextButton(onClick = { onEdit(booking) }) {
                Text(
                    text = "Edit",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }
        }
    }
}
