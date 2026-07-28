package com.yellowtrack.platform.feature.ledger.presentation.component

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
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.expense.ExpenseCategory
import com.yellowtrack.platform.feature.ledger.presentation.model.NewExpense
import com.yellowtrack.platform.feature.ledger.presentation.model.ProjectOption
import kotlinx.datetime.LocalDate

/**
 * Records money the studio spent.
 *
 * The project field is the consequential one, and the form says so: leaving it as overhead
 * spreads the cost across every job through the pricing floor, while attaching it to a
 * booking charges it to that job's margin. The category picks a sensible default, which
 * the studio can override.
 */
@Composable
internal fun ExpenseFormDialog(
    today: LocalDate,
    currency: CurrencyCode,
    projects: List<ProjectOption>,
    onSave: (NewExpense) -> Unit,
    onDismiss: () -> Unit,
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExpenseCategory.Software) }
    var incurredOn by remember { mutableStateOf(today.toString()) }
    var vendor by remember { mutableStateOf("") }
    var deductible by remember { mutableStateOf(true) }

    val overhead = ProjectOption(id = null, label = "Overhead — not a specific job")
    val options = remember(projects) { listOf(overhead) + projects }
    var selectedProject by remember(projects) { mutableStateOf(overhead) }

    // Changing category re-suggests overhead or job cost, without locking the choice.
    val suggestedByCategory =
        remember(category) { if (category.isTypicallyOverhead) overhead else null }

    val amountValid = parseMoney(amount, currency)?.isPositive == true
    val dateValid = runCatching { LocalDate.parse(incurredOn) }.isSuccess

    YTFormDialog(
        title = "Record a cost",
        confirmLabel = "Save",
        confirmEnabled = description.isNotBlank() && amountValid && dateValid,
        onConfirm = {
            onSave(
                NewExpense(
                    description = description.trim(),
                    amount = amount.trim(),
                    category = category,
                    incurredOn = incurredOn.trim(),
                    projectId = selectedProject.id,
                    vendor = vendor.trim().ifBlank { null },
                    isTaxDeductible = deductible,
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = description,
            onValueChange = { description = it },
            label = "What was it?",
            placeholder = "Studio and gear insurance",
        )

        YTTextField(
            value = amount,
            onValueChange = { amount = it },
            label = "Amount (${currency.code})",
            keyboardType = KeyboardType.Decimal,
            errorMessage = if (amount.isNotBlank() && !amountValid) "Enter an amount such as 1250.00" else null,
        )

        YTDropdownField(
            label = "Category",
            selected = category,
            options = ExpenseCategory.entries,
            optionLabel = { it.name },
            onSelect = { newCategory ->
                category = newCategory
                if (newCategory.isTypicallyOverhead) selectedProject = overhead
            },
        )

        YTDropdownField(
            label = "Charge to",
            selected = selectedProject,
            options = options,
            optionLabel = ProjectOption::label,
            onSelect = { selectedProject = it },
            help =
                if (selectedProject.id == null) {
                    "Overhead is spread across every job through your pricing floor."
                } else {
                    "Charged to this booking and subtracted from what it earned."
                },
        )

        YTTextField(
            value = incurredOn,
            onValueChange = { incurredOn = it },
            label = "Date",
            placeholder = "2026-07-28",
            errorMessage = if (incurredOn.isNotBlank() && !dateValid) "Use the form 2026-07-28" else null,
        )

        YTTextField(
            value = vendor,
            onValueChange = { vendor = it },
            label = "Paid to",
            imeAction = ImeAction.Done,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
        ) {
            Checkbox(
                checked = deductible,
                onCheckedChange = { deductible = it },
            )
            Text(
                text = "Tax deductible",
                style = YTTheme.typography.bodyMedium,
                color = YTTheme.colors.onSurface,
            )
        }

        // Referenced so the suggestion is not silently computed and discarded.
        if (suggestedByCategory != null && selectedProject.id != null) {
            Text(
                text = "${category.name} is usually overhead rather than a job cost.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}
