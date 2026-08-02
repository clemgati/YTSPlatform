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
import com.yellowtrack.platform.core.model.expense.DistanceUnit
import com.yellowtrack.platform.feature.ledger.presentation.model.NewMileage
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import kotlinx.datetime.LocalDate

/**
 * Records a journey driven for the business.
 *
 * The application has held mileage since 0.4.0 — the model, the table, the deduction on the
 * Ledger — and until now nothing could create one, so that deduction could only ever read
 * zero. This is a claim against tax that a studio was quietly not making.
 *
 * The rate is entered rather than assumed. It differs by country, by year and by vehicle,
 * and a figure this application invented would be wrong in a way nobody would check.
 */
@Composable
internal fun MileageFormDialog(
    today: LocalDate,
    currency: CurrencyCode,
    projects: List<ProjectOption>,
    onSave: (NewMileage) -> Unit,
    onDismiss: () -> Unit,
    /** The journey being corrected, or null when this is a new one. */
    initial: NewMileage? = null,
) {
    var travelledOn by remember { mutableStateOf(initial?.travelledOn ?: today.toString()) }
    var distance by remember { mutableStateOf(initial?.distance.orEmpty()) }
    var unit by remember { mutableStateOf(initial?.unit ?: DistanceUnit.Miles) }
    var rate by remember { mutableStateOf(initial?.ratePerUnit.orEmpty()) }
    var purpose by remember { mutableStateOf(initial?.purpose.orEmpty()) }
    var from by remember { mutableStateOf(initial?.fromLocation.orEmpty()) }
    var to by remember { mutableStateOf(initial?.toLocation.orEmpty()) }

    val overhead = ProjectOption(id = null, label = "Overhead — not a specific job")
    val options = remember(projects) { listOf(overhead) + projects }
    var selectedProject by
        remember(projects) {
            mutableStateOf(options.firstOrNull { it.id == initial?.projectId } ?: overhead)
        }

    val distanceValid = distance.trim().toDoubleOrNull()?.let { it > 0 } == true
    val rateValid = parseMoney(rate, currency)?.isPositive == true
    val dateValid = runCatching { LocalDate.parse(travelledOn) }.isSuccess

    YTFormDialog(
        title = if (initial == null) "Record a journey" else "Correct this journey",
        confirmLabel = if (initial == null) "Save" else "Save changes",
        confirmEnabled = distanceValid && rateValid && dateValid,
        onConfirm = {
            onSave(
                NewMileage(
                    travelledOn = travelledOn.trim(),
                    distance = distance.trim(),
                    unit = unit,
                    ratePerUnit = rate.trim(),
                    projectId = selectedProject.id,
                    purpose = purpose.trim().ifBlank { null },
                    fromLocation = from.trim().ifBlank { null },
                    toLocation = to.trim().ifBlank { null },
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = travelledOn,
            onValueChange = { travelledOn = it },
            label = "When",
            placeholder = today.toString(),
            errorMessage = "Use a date like $today".takeIf { travelledOn.isNotBlank() && !dateValid },
        )

        YTTextField(
            value = distance,
            onValueChange = { distance = it },
            label = "How far",
            placeholder = "42",
            keyboardType = KeyboardType.Decimal,
        )

        YTDropdownField(
            label = "Measured in",
            options = DistanceUnit.entries,
            selected = unit,
            onSelect = { unit = it },
            optionLabel = { it.name },
        )

        YTTextField(
            value = rate,
            onValueChange = { rate = it },
            label = "Rate per ${unit.name.lowercase().trimEnd('s')}",
            placeholder = "0.45",
            keyboardType = KeyboardType.Decimal,
            help =
                "Whatever your tax authority allows this year. It changes, so it is asked for " +
                    "rather than assumed.",
        )

        YTDropdownField(
            label = "Charge it to",
            options = options,
            selected = selectedProject,
            onSelect = { selectedProject = it },
            optionLabel = { it.label },
        )

        YTTextField(
            value = purpose,
            onValueChange = { purpose = it },
            label = "What for",
            placeholder = "Venue recce",
        )

        YTTextField(
            value = from,
            onValueChange = { from = it },
            label = "From",
            placeholder = "Studio",
        )

        YTTextField(
            value = to,
            onValueChange = { to = it },
            label = "To",
            placeholder = "Trebah Garden",
            imeAction = ImeAction.Done,
        )
    }
}
