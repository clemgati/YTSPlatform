package com.yellowtrack.platform.feature.ledger.presentation.component

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
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.parseMoney
import com.yellowtrack.platform.core.common.money.parsePercentageToBasisPoints
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.billing.grandTotal
import com.yellowtrack.platform.core.model.billing.subtotal
import com.yellowtrack.platform.core.model.billing.taxTotal
import com.yellowtrack.platform.feature.ledger.presentation.mapper.display
import com.yellowtrack.platform.feature.ledger.presentation.model.NewLineItem
import com.yellowtrack.platform.feature.ledger.presentation.model.toLineItem

/**
 * One line's fields, as typed.
 *
 * Every value a row shows lives here rather than inside the row, so the list is the whole
 * truth: removing a line cannot leave stale text behind in the row that takes its place.
 */
internal data class LineFields(
    val description: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
    val taxRate: String = "",
) {
    companion object {
        /** The stored line as the editor holds it, so a draft reopens on what was built. */
        fun of(line: NewLineItem) =
            LineFields(
                description = line.description,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
                taxRate = line.taxRate,
            )
    }

    fun asNew(): NewLineItem =
        NewLineItem(
            description = description,
            quantity = quantity,
            unitPrice = unitPrice,
            taxRate = taxRate,
        )
}

/**
 * Edits the lines of a quote or an invoice.
 *
 * The running total is the point of this component. A studio pricing a wedding adds
 * coverage, a second shooter, and an album, and the figure that decides whether the job is
 * worth taking is the one at the bottom — computed here by exactly the rule that will
 * store it, so what is watched while typing is what the client is sent.
 *
 * Tax is shown as its own line only when there is some, because a zero tax row on every
 * wedding quote is noise that teaches people to stop reading the total.
 */
@Composable
internal fun LineItemsEditor(
    lines: List<LineFields>,
    currency: CurrencyCode,
    onChange: (Int, LineFields) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsed = lines.mapNotNull { it.asNew().toLineItem(currency) }
    val tax = parsed.taxTotal(currency)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.medium),
    ) {
        lines.forEachIndexed { index, line ->
            LineRow(
                line = line,
                currency = currency,
                // The last line standing cannot be removed: a document with no lines has
                // no figure, and the studio would be left with a form it cannot save and
                // no way back other than cancelling.
                canRemove = lines.size > 1,
                onChange = { onChange(index, it) },
                onRemove = { onRemove(index) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onAdd) {
                Text(
                    text = "Add a line",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.primary,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (!tax.isZero) {
                    Text(
                        text = "${parsed.subtotal(currency).display()} + ${tax.display()} tax",
                        style = YTTheme.typography.bodySmall,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                }

                Text(
                    text = parsed.grandTotal(currency).display(),
                    style = YTTheme.typography.titleMedium,
                    color = YTTheme.colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LineRow(
    line: LineFields,
    currency: CurrencyCode,
    canRemove: Boolean,
    onChange: (LineFields) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.extraSmall)) {
        HorizontalDivider(color = YTTheme.colors.outlineVariant)

        YTTextField(
            value = line.description,
            onValueChange = { onChange(line.copy(description = it)) },
            label = "What is it?",
            placeholder = "Wedding coverage, eight hours",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(YTTheme.spacing.small),
            verticalAlignment = Alignment.Top,
        ) {
            YTTextField(
                value = line.quantity,
                onValueChange = { onChange(line.copy(quantity = it)) },
                label = "Qty",
                modifier = Modifier.weight(QUANTITY_WEIGHT),
                keyboardType = KeyboardType.Number,
                errorMessage = if (!line.quantityValid) "Whole number" else null,
            )

            YTTextField(
                value = line.unitPrice,
                onValueChange = { onChange(line.copy(unitPrice = it)) },
                label = "Each (${currency.code})",
                modifier = Modifier.weight(PRICE_WEIGHT),
                keyboardType = KeyboardType.Decimal,
                errorMessage = if (!line.unitPriceValid(currency)) "e.g. 4000.00" else null,
            )

            YTTextField(
                value = line.taxRate,
                onValueChange = { onChange(line.copy(taxRate = it)) },
                label = "Tax %",
                modifier = Modifier.weight(TAX_WEIGHT),
                keyboardType = KeyboardType.Decimal,
                errorMessage = if (!line.taxRateValid) "e.g. 8.25" else null,
            )
        }

        if (canRemove) {
            TextButton(onClick = onRemove) {
                Text(
                    text = "Remove",
                    style = YTTheme.typography.labelLarge,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Whether every line holds up, which is what decides if the document may be saved.
 *
 * An untouched line counts as incomplete rather than absent: a studio that adds a line and
 * then fills in nothing has said it means to bill for something, and silently discarding
 * it would send a smaller figure than the one on screen.
 */
internal fun List<LineFields>.allValid(currency: CurrencyCode): Boolean =
    isNotEmpty() && all { it.asNew().toLineItem(currency) != null }

// Field-level checks, for showing an error against the box being typed in. What decides
// whether the document may be saved is [allValid], which goes through the same rule the
// ViewModel will. Blank reads as untouched rather than wrong, so a line added and not yet
// filled in is not born covered in errors.

private val LineFields.quantityValid: Boolean
    get() = quantity.isBlank() || (quantity.toIntOrNull()?.let { it > 0 } == true)

private fun LineFields.unitPriceValid(currency: CurrencyCode): Boolean =
    unitPrice.isBlank() || parseMoney(unitPrice, currency)?.isPositive == true

private val LineFields.taxRateValid: Boolean
    get() = taxRate.isBlank() || (parsePercentageToBasisPoints(taxRate)?.let { it >= 0 } == true)

private const val QUANTITY_WEIGHT = 0.7f

private const val PRICE_WEIGHT = 1.4f

private const val TAX_WEIGHT = 1f
