package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.ledger.presentation.model.NewQuote
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Prices a proposal and sends it.
 *
 * The validity date is prefilled rather than left blank, because an open-ended quote is a
 * price the studio has to honour indefinitely — through cost increases and through dates
 * it could have sold twice.
 */
@Composable
internal fun QuoteFormDialog(
    suggestedNumber: String,
    today: LocalDate,
    currency: CurrencyCode,
    projects: List<ProjectOption>,
    onSave: (NewQuote) -> Unit,
    onDismiss: () -> Unit,
    /** The quote being revised, or null when this is a new one. */
    initial: NewQuote? = null,
) {
    val bookings = remember(projects) { projects.filter { it.id != null } }

    var number by remember { mutableStateOf(initial?.number ?: suggestedNumber) }
    var validUntil by
        remember {
            mutableStateOf(
                initial?.validUntil ?: today.plus(DEFAULT_VALIDITY_DAYS, DateTimeUnit.DAY).toString(),
            )
        }
    var terms by remember { mutableStateOf(initial?.terms.orEmpty()) }
    var selectedProject by
        remember(bookings) {
            mutableStateOf(bookings.firstOrNull { it.id == initial?.projectId } ?: bookings.firstOrNull())
        }

    val lines =
        remember {
            mutableStateListOf(
                *initial
                    ?.lines
                    .orEmpty()
                    .map(LineFields::of)
                    .ifEmpty { listOf(LineFields()) }
                    .toTypedArray(),
            )
        }

    val validUntilValid = validUntil.isBlank() || runCatching { LocalDate.parse(validUntil) }.isSuccess
    val booking = selectedProject

    YTFormDialog(
        title = if (initial == null) "Send a quote" else "Revise this quote",
        confirmLabel = if (initial == null) "Send" else "Save changes",
        supportingText =
            if (bookings.isEmpty()) {
                "A quote is priced against a booking, and there are none yet."
            } else {
                null
            },
        confirmEnabled =
            booking?.id != null &&
                number.isNotBlank() &&
                lines.allValid(currency) &&
                validUntilValid,
        onConfirm = {
            val projectId = booking?.id ?: return@YTFormDialog

            onSave(
                NewQuote(
                    number = number.trim(),
                    projectId = projectId,
                    lines = lines.map(LineFields::asNew),
                    validUntil = validUntil.trim(),
                    terms = terms.trim().ifBlank { null },
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        if (booking != null) {
            YTDropdownField(
                label = "For",
                selected = booking,
                options = bookings,
                optionLabel = ProjectOption::label,
                onSelect = { selectedProject = it },
            )
        }

        YTTextField(
            value = number,
            onValueChange = { number = it },
            label = "Quote number",
        )

        LineItemsEditor(
            lines = lines,
            currency = currency,
            onChange = { index, updated -> lines[index] = updated },
            onAdd = { lines.add(LineFields()) },
            onRemove = { index -> lines.removeAt(index) },
        )

        YTTextField(
            value = validUntil,
            onValueChange = { validUntil = it },
            label = "Price held until",
            placeholder = today.toString(),
            errorMessage = if (!validUntilValid) "Use the form $today" else null,
        )

        if (validUntil.isBlank()) {
            Text(
                text = "With no date, this price stands indefinitely — through cost rises and dates you turn away.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.error,
            )
        }

        YTTextField(
            value = terms,
            onValueChange = { terms = it },
            label = "Terms",
            placeholder = "Fifty per cent retainer secures the date.",
            imeAction = ImeAction.Done,
        )
    }
}

/** Long enough for a client to decide, short enough that a price is not held for a season. */
private const val DEFAULT_VALIDITY_DAYS = 30
