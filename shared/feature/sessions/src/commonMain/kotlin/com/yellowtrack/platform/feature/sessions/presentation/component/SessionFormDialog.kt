package com.yellowtrack.platform.feature.sessions.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.feature.sessions.presentation.model.BookingOption
import com.yellowtrack.platform.feature.sessions.presentation.model.NewSession
import com.yellowtrack.platform.feature.sessions.presentation.model.timing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Puts a day of work on the calendar, inside a booking.
 *
 * The booking comes first because a session that belongs to nothing cannot be costed,
 * invoiced, or answered for — a shoot day is part of a job, not a free-floating diary
 * entry.
 *
 * The duration is shown back rather than left implicit. A wedding running 14:00 to 01:00
 * is entered exactly as it reads and resolves to eleven hours the next morning; a typo
 * resolves to an implausible number of hours, and says so on the form.
 *
 * Pass [initial] to open an existing session. Editing then distinguishes two things the
 * model already separates: correcting details that were wrong, and a date that has *moved*
 * — for which the original block is kept, marked Postponed, rather than overwritten. A
 * cancelled shoot the studio has no record of is a cancelled shoot nobody can be charged
 * for.
 */
@Composable
internal fun SessionFormDialog(
    bookings: List<BookingOption>,
    today: LocalDate,
    zone: TimeZone,
    onSave: (NewSession, movedToNewDate: Boolean) -> Unit,
    onDismiss: () -> Unit,
    initial: NewSession? = null,
) {
    var selectedBooking by
        remember(bookings, initial) {
            mutableStateOf(
                bookings.firstOrNull { it.id == initial?.projectId } ?: bookings.firstOrNull(),
            )
        }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: SessionKind.Shoot) }
    var status by remember { mutableStateOf(initial?.status ?: SessionStatus.Scheduled) }
    var date by remember { mutableStateOf(initial?.date ?: today.toString()) }
    var startTime by remember { mutableStateOf(initial?.startTime.orEmpty()) }
    var endTime by remember { mutableStateOf(initial?.endTime.orEmpty()) }
    var callTime by remember { mutableStateOf(initial?.callTime.orEmpty()) }
    var locationName by remember { mutableStateOf(initial?.locationName.orEmpty()) }
    var locationAddress by remember { mutableStateOf(initial?.locationAddress.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var movedToNewDate by remember { mutableStateOf(false) }

    val booking = selectedBooking

    val form =
        booking?.let {
            NewSession(
                projectId = it.id,
                title = title,
                kind = kind,
                status = status,
                date = date,
                startTime = startTime,
                endTime = endTime,
                callTime = callTime,
                locationName = locationName,
                locationAddress = locationAddress,
                notes = notes,
            )
        }

    val timing = form?.timing(zone)

    YTFormDialog(
        title = if (initial == null) "Schedule a session" else "Edit session",
        confirmLabel =
            when {
                initial == null -> "Save"
                movedToNewDate -> "Move it"
                else -> "Save changes"
            },
        supportingText =
            if (bookings.isEmpty()) {
                "A session belongs to a booking, and there are none yet. Open one from a client first."
            } else {
                null
            },
        confirmEnabled = form != null && title.isNotBlank() && timing != null,
        onConfirm = { form?.let { onSave(it, movedToNewDate) } },
        onDismiss = onDismiss,
    ) {
        if (booking != null) {
            YTDropdownField(
                label = "Booking",
                selected = booking,
                options = bookings,
                optionLabel = BookingOption::label,
                onSelect = { selectedBooking = it },
            )
        }

        YTTextField(
            value = title,
            onValueChange = { title = it },
            label = "What is this day?",
            placeholder = "Wedding day",
        )

        YTDropdownField(
            label = "Kind",
            selected = kind,
            options = SessionKind.entries,
            optionLabel = { it.name },
            onSelect = { kind = it },
            optionDescription = { it.explanation },
        )

        YTDropdownField(
            label = "Status",
            selected = status,
            options = SessionStatus.entries,
            optionLabel = { it.name },
            onSelect = { status = it },
            optionDescription = { it.explanation },
        )

        YTTextField(
            value = date,
            onValueChange = { date = it },
            label = "Date",
            placeholder = today.toString(),
        )

        YTTextField(
            value = startTime,
            onValueChange = { startTime = it },
            label = "Starts",
            placeholder = "14:00",
        )

        YTTextField(
            value = endTime,
            onValueChange = { endTime = it },
            label = "Ends",
            placeholder = "23:00",
            help = "An earlier time than the start means the next morning.",
        )

        YTTextField(
            value = callTime,
            onValueChange = { callTime = it },
            label = "Crew called at",
            placeholder = "13:00",
            help = "The time that matters to anyone being paid to show up.",
        )

        YTTextField(
            value = locationName,
            onValueChange = { locationName = it },
            label = "Where",
            placeholder = "Thornbury Manor",
        )

        YTTextField(
            value = locationAddress,
            onValueChange = { locationAddress = it },
            label = "Address",
        )

        YTTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes",
            singleLine = false,
            imeAction = ImeAction.Done,
        )

        if (initial != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
            ) {
                Checkbox(
                    checked = movedToNewDate,
                    onCheckedChange = { movedToNewDate = it },
                )
                Text(
                    text = "The date moved",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurface,
                )
            }

            Text(
                text =
                    if (movedToNewDate) {
                        "The original day stays on the calendar as postponed, and this is scheduled as a new one."
                    } else {
                        "Correcting this day in place. Tick the box if the shoot itself moved to another date."
                    },
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }

        if (timing != null) {
            Text(
                text = timing.durationLabel,
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        } else if (startTime.isNotBlank() || endTime.isNotBlank()) {
            Text(
                text = "Use 24-hour times such as 14:00, and a date such as $today.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.error,
            )
        }
    }
}

private val com.yellowtrack.platform.feature.sessions.presentation.model.SessionTiming.durationLabel: String
    get() {
        val hours = duration.inWholeMinutes / 60
        val minutes = duration.inWholeMinutes % 60

        return when {
            hours == 0L -> "$minutes minutes on site"
            minutes == 0L -> "$hours hours on site"
            else -> "${hours}h ${minutes}m on site"
        }
    }

private val SessionKind.explanation: String
    get() =
        when (this) {
            SessionKind.Consultation -> "Discovery call, before or just after booking."
            SessionKind.Scout -> "Location recce. Billable on commercial work."
            SessionKind.Shoot -> "The shoot itself."
            SessionKind.Pickup -> "Extra footage or stills after the main shoot."
            SessionKind.Delivery -> "Album reveal or ordering appointment."
        }

private val SessionStatus.explanation: String
    get() =
        when (this) {
            SessionStatus.Scheduled -> "On the calendar, not yet confirmed with the client."
            SessionStatus.Confirmed -> "Confirmed. Weather and travel are now your problem."
            SessionStatus.InProgress -> "Underway."
            SessionStatus.Completed -> "Shot. Media may not yet be offloaded."
            SessionStatus.Postponed -> "Moved to a new date."
            SessionStatus.Cancelled -> "Cancelled."
        }
