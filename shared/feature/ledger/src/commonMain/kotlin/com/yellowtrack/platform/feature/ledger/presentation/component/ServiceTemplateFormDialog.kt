package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.feature.ledger.presentation.model.NewServiceTemplate

/**
 * A package the studio sells, and what it costs the studio to deliver.
 *
 * Until now the four seeded packages were the only ones that could ever exist. A studio
 * could not add the thing it actually sells, and could not correct a default's price — so
 * the pricing floor, whose entire job is to say which packages fall short, was measuring
 * packages nobody had agreed to.
 *
 * The duration and session count are asked for because they are what the floor multiplies:
 * a package is compared against the floor by the days it consumes, and days come from
 * shooting time plus the post-production that follows it. A package with no honest duration
 * gets an honest-looking comparison against the wrong number.
 */
@Composable
internal fun ServiceTemplateFormDialog(
    currency: CurrencyCode,
    onSave: (NewServiceTemplate) -> Unit,
    onDismiss: () -> Unit,
    /** The package being corrected, or null when this is a new one. */
    initial: NewServiceTemplate? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var serviceLine by remember { mutableStateOf(initial?.serviceLine ?: ServiceLine.Portrait) }
    var duration by remember { mutableStateOf(initial?.sessionDurationMinutes ?: "120") }
    var sessions by remember { mutableStateOf(initial?.sessionCount ?: "1") }
    var price by remember { mutableStateOf(initial?.basePrice.orEmpty()) }
    var deliverables by remember { mutableStateOf(initial?.deliverableCount.orEmpty()) }
    var turnaround by remember { mutableStateOf(initial?.turnaroundDays.orEmpty()) }
    var revisions by remember { mutableStateOf(initial?.revisionRounds.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }

    val durationValid = duration.trim().toIntOrNull()?.let { it > 0 } == true
    val sessionsValid = sessions.trim().toIntOrNull()?.let { it > 0 } == true

    // Blank is allowed and means undecided. A figure that was typed and does not parse is
    // not the same thing, and saving it as "no price" would quietly discard what was meant.
    val priceValid = price.isBlank() || parseMoney(price, currency)?.isPositive == true

    YTFormDialog(
        title = if (initial == null) "Add a package" else "Edit this package",
        confirmLabel = if (initial == null) "Add" else "Save changes",
        confirmEnabled = name.isNotBlank() && durationValid && sessionsValid && priceValid,
        onConfirm = {
            onSave(
                NewServiceTemplate(
                    name = name.trim(),
                    serviceLine = serviceLine,
                    sessionDurationMinutes = duration.trim(),
                    sessionCount = sessions.trim(),
                    basePrice = price.trim(),
                    deliverableCount = deliverables.trim(),
                    turnaroundDays = turnaround.trim(),
                    revisionRounds = revisions.trim(),
                    notes = notes.trim(),
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = name,
            onValueChange = { name = it },
            label = "What it is called",
            placeholder = "Half-day wedding coverage",
        )

        YTDropdownField(
            label = "Kind of work",
            options = ServiceLine.entries,
            selected = serviceLine,
            onSelect = { serviceLine = it },
            optionLabel = { it.name },
        )

        YTTextField(
            value = duration,
            onValueChange = { duration = it },
            label = "Shooting time, in minutes",
            placeholder = "120",
            keyboardType = KeyboardType.Number,
            help = "What the floor multiplies. Post-production is added on top of this.",
        )

        YTTextField(
            value = sessions,
            onValueChange = { sessions = it },
            label = "How many sessions",
            placeholder = "1",
            keyboardType = KeyboardType.Number,
        )

        YTTextField(
            value = price,
            onValueChange = { price = it },
            label = "What you charge",
            placeholder = "1200.00",
            keyboardType = KeyboardType.Decimal,
            help = "Leave blank if it is not decided. The floor is shown either way.",
        )

        YTTextField(
            value = deliverables,
            onValueChange = { deliverables = it },
            label = "Images delivered",
            placeholder = "60",
            keyboardType = KeyboardType.Number,
        )

        YTTextField(
            value = turnaround,
            onValueChange = { turnaround = it },
            label = "Turnaround, in days",
            placeholder = "21",
            keyboardType = KeyboardType.Number,
        )

        YTTextField(
            value = revisions,
            onValueChange = { revisions = it },
            label = "Rounds of revisions",
            placeholder = "1",
            keyboardType = KeyboardType.Number,
        )

        YTTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Anything else",
            placeholder = "Includes travel within 30 miles",
            imeAction = ImeAction.Done,
        )
    }
}
