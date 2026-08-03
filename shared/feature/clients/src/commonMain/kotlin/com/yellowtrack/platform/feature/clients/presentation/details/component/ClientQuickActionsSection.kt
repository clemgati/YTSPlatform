package com.yellowtrack.platform.feature.clients.presentation.details.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTDetailSection
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientRemoval

/**
 * Archiving is deliberately absent. The button existed and did nothing — `Client` has no
 * archived state to set, so there was nothing for it to do. A control that silently
 * ignores a press is worse than one that is not offered.
 *
 * Removal is the opposite case and arrived later: for four versions a client entered by
 * mistake could be edited into something else but never taken away, while a lighting
 * recipe could be deleted outright. The account is the first thing a studio types into
 * this application and was the last thing it could undo.
 */
@Composable
internal fun ClientQuickActionsSection(
    onAddProject: () -> Unit,
    onScheduleSession: () -> Unit,
    onEditClient: () -> Unit,
    removal: ClientRemoval,
    onRemoveClient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    YTDetailSection(
        title = "Quick Actions",
        modifier = modifier,
    ) {
        Column(
            verticalArrangement =
                Arrangement.spacedBy(
                    YTTheme.spacing.small,
                ),
        ) {
            YTButton(
                text = "Open a Booking",
                onClick = onAddProject,
                modifier = Modifier.fillMaxWidth(),
            )

            YTButton(
                text = "Schedule Session",
                onClick = onScheduleSession,
                modifier = Modifier.fillMaxWidth(),
            )

            YTButton(
                text = "Edit Client",
                onClick = onEditClient,
                modifier = Modifier.fillMaxWidth(),
            )

            // Not a fourth yellow button. Rendered beside the other three it was the most
            // prominent control on the card and indistinguishable from "Open a Booking",
            // which is the wrong weight entirely for the one action here that cannot be
            // undone. It matches how a payment is taken off an invoice: quiet, and red.
            TextButton(
                onClick = onRemoveClient,
                enabled = removal is ClientRemoval.Available,
            ) {
                Text(
                    text = "Remove Client",
                    style = YTTheme.typography.labelLarge,
                    color =
                        when (removal) {
                            is ClientRemoval.Available -> YTTheme.colors.error
                            else -> YTTheme.colors.onSurfaceVariant
                        },
                )
            }

            // Shown rather than left to be inferred. A control that is greyed out without
            // saying why reads as a fault in the application, and the studio's next move —
            // remove the bookings, or keep the account — depends entirely on the reason.
            if (removal is ClientRemoval.HeldByBookings) {
                Text(
                    text =
                        when (removal.count) {
                            1 -> "This account has a booking on it. Remove the booking first."
                            else -> "This account has ${removal.count} bookings on it. Remove them first."
                        },
                    style = YTTheme.typography.bodySmall,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
