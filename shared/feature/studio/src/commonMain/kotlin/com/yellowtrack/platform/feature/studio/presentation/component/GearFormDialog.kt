package com.yellowtrack.platform.feature.studio.presentation.component

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
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearStatus
import com.yellowtrack.platform.feature.studio.presentation.mapper.label
import com.yellowtrack.platform.feature.studio.presentation.model.NewGearItem
import kotlinx.datetime.LocalDate

/**
 * Adds a piece of gear.
 *
 * Only the name is required. Serial number and price are the fields that decide an
 * insurance claim, so the form says why rather than marking them optional and letting a
 * studio skip them without knowing what it is skipping.
 */
@Composable
internal fun GearFormDialog(
    today: LocalDate,
    currency: CurrencyCode,
    onSave: (NewGearItem) -> Unit,
    onDismiss: () -> Unit,
    /** The item being corrected, or null when this is a new one. */
    initial: NewGearItem? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category ?: GearCategory.Camera) }
    var status by remember { mutableStateOf(initial?.status ?: GearStatus.InService) }
    var serialNumber by remember { mutableStateOf(initial?.serialNumber.orEmpty()) }
    var price by remember { mutableStateOf(initial?.purchasePrice.orEmpty()) }
    var purchasedOn by remember { mutableStateOf(initial?.purchasedOn.orEmpty()) }
    var servicedOn by remember { mutableStateOf(initial?.lastServicedOn.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }

    val priceValid = price.isBlank() || parseMoney(price, currency) != null
    val purchasedValid = purchasedOn.isBlank() || purchasedOn.isDate()
    val servicedValid = servicedOn.isBlank() || servicedOn.isDate()

    YTFormDialog(
        title = if (initial == null) "Add gear" else "Edit this gear",
        confirmLabel = if (initial == null) "Save" else "Save changes",
        confirmEnabled = name.isNotBlank() && priceValid && purchasedValid && servicedValid,
        onConfirm = {
            onSave(
                NewGearItem(
                    name = name,
                    category = category,
                    status = status,
                    serialNumber = serialNumber.ifBlank { null },
                    purchasePrice = price.ifBlank { null },
                    purchasedOn = purchasedOn.ifBlank { null },
                    lastServicedOn = servicedOn.ifBlank { null },
                    notes = notes.ifBlank { null },
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = name,
            onValueChange = { name = it },
            label = "What is it?",
            placeholder = "Canon R5 body",
        )

        YTDropdownField(
            label = "Category",
            selected = category,
            options = GearCategory.entries,
            optionLabel = { it.label },
            onSelect = { category = it },
        )

        YTTextField(
            value = serialNumber,
            onValueChange = { serialNumber = it },
            label = "Serial number",
            help = "The field an insurer settles on, and the one police can trace. Leave blank if it has none.",
        )

        YTTextField(
            value = price,
            onValueChange = { price = it },
            label = "What it cost (${currency.code})",
            keyboardType = KeyboardType.Decimal,
            help = "Used for the insured total. Leave blank and the total understates by that much.",
            errorMessage = if (!priceValid) "Enter an amount such as 3899.00" else null,
        )

        YTTextField(
            value = purchasedOn,
            onValueChange = { purchasedOn = it },
            label = "Bought on",
            placeholder = today.toString(),
            errorMessage = if (!purchasedValid) "Use the form $today" else null,
        )

        YTTextField(
            value = servicedOn,
            onValueChange = { servicedOn = it },
            label = "Last serviced",
            placeholder = today.toString(),
            errorMessage = if (!servicedValid) "Use the form $today" else null,
        )

        YTDropdownField(
            label = "Status",
            selected = status,
            options = GearStatus.entries,
            optionLabel = { it.label },
            onSelect = { status = it },
            help =
                when (status) {
                    GearStatus.InService -> "Available to pack."
                    GearStatus.InRepair -> "Still owned and still insured, but not available on Saturday."
                    GearStatus.Retired -> "Kept for the record and left out of the insured total."
                    GearStatus.Lost -> "Still on the insurance schedule, because it can still be claimed for."
                },
        )

        YTTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes",
            imeAction = ImeAction.Done,
        )
    }
}

private fun String.isDate(): Boolean = runCatching { LocalDate.parse(trim()) }.isSuccess
