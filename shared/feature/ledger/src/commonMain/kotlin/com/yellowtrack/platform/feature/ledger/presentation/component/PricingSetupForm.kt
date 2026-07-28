package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.money.parsePercentageToBasisPoints
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.theme.YTTheme

/**
 * The four figures the pricing floor cannot be computed without.
 *
 * Each field carries its own explanation, because the one people get badly wrong —
 * billable days — is wrong in a predictable way: they count the days they shoot rather
 * than the days they can sell, and the resulting floor comes out several times too low.
 */
@Composable
internal fun PricingSetupForm(
    initialSalary: String,
    initialBillableDays: String,
    initialTaxRate: String,
    currency: CurrencyCode,
    onSave: (salary: String, billableDays: String, taxRate: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var salary by remember(initialSalary) { mutableStateOf(initialSalary) }
    var billableDays by remember(initialBillableDays) { mutableStateOf(initialBillableDays) }
    var taxRate by remember(initialTaxRate) { mutableStateOf(initialTaxRate) }

    val salaryValid = parseMoney(salary, currency)?.isPositive == true
    val daysValid = billableDays.toIntOrNull()?.let { it in 1..366 } == true
    val taxValid = taxRate.isBlank() || parsePercentageToBasisPoints(taxRate)?.let { it < 10_000 } == true

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium),
    ) {
        FormField(
            value = salary,
            onValueChange = { salary = it },
            label = "Take-home target (${currency.code})",
            help = "What you intend to pay yourself for the year, after tax.",
            isError = salary.isNotBlank() && !salaryValid,
            keyboardType = KeyboardType.Decimal,
        )

        FormField(
            value = billableDays,
            onValueChange = { billableDays = it },
            label = "Sellable days a year",
            help =
                "Days you can actually sell — not days you work. Editing, admin, and " +
                    "marketing all consume days you cannot bill for, so this is usually far " +
                    "lower than people expect.",
            isError = billableDays.isNotBlank() && !daysValid,
            keyboardType = KeyboardType.Number,
        )

        FormField(
            value = taxRate,
            onValueChange = { taxRate = it },
            label = "Effective tax rate (%)",
            help =
                "Including self-employment or national insurance contributions. Leave blank " +
                    "if you would rather handle tax separately.",
            isError = taxRate.isNotBlank() && !taxValid,
            keyboardType = KeyboardType.Decimal,
        )

        YTButton(
            text = "Save pricing basis",
            onClick = { onSave(salary, billableDays, taxRate) },
            enabled = salaryValid && daysValid && taxValid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    help: String,
    isError: Boolean,
    keyboardType: KeyboardType,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            shape = YTTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        )

        Text(
            text = help,
            style = YTTheme.typography.bodySmall,
            color = if (isError) YTTheme.colors.error else YTTheme.colors.onSurfaceVariant,
        )
    }
}
