package com.yellowtrack.platform.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.yellowtrack.platform.core.designsystem.component.YTButton
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.sync.SyncConflictId
import com.yellowtrack.platform.core.ui.component.StatefulContent

/**
 * The studio's own details.
 *
 * Every field here appears on something a client or a second shooter reads, so each one
 * says what it is for rather than merely what it is called. A studio filling this in has
 * no way to know that leaving the tax number blank makes the invoice non-deductible for
 * its client unless the form says so.
 */
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onRetry: () -> Unit,
    onSave: (StudioProfileFields) -> Unit,
    onDismissConflict: (SyncConflictId) -> Unit,
    modifier: Modifier = Modifier,
) {
    StatefulContent(
        state = uiState.content,
        modifier = modifier.fillMaxSize(),
        onRetry = onRetry,
    ) { content, contentModifier ->
        // Keyed on what was loaded, so the form re-seeds once the saved profile arrives
        // and does not discard what is being typed on every unrelated emission.
        var fields by remember(content.profile) { mutableStateOf(content.profile) }

        Column(
            modifier =
                contentModifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(YTTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.large),
        ) {
            Text(
                text = "Settings",
                style = YTTheme.typography.headlineLarge,
                color = YTTheme.colors.onBackground,
            )

            if (content.conflicts.isNotEmpty()) {
                ConflictsSection(
                    conflicts = content.conflicts,
                    onDismiss = onDismissConflict,
                )
            }

            YTSectionCard(title = "Your studio") {
                Text(
                    text =
                        "These go on everything you send — call sheets, quotes, and invoices. " +
                            "Without a name, nothing can be sent at all.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                YTTextField(
                    value = fields.name,
                    onValueChange = { fields = fields.copy(name = it) },
                    label = "Studio name",
                    placeholder = "Yellow Track Studios",
                    errorMessage =
                        "A document with no name on it is not a document"
                            .takeIf { fields.name.isBlank() },
                )

                YTTextField(
                    value = fields.address,
                    onValueChange = { fields = fields.copy(address = it) },
                    label = "Address",
                    singleLine = false,
                    help = "As it should print. Several lines are fine.",
                )

                YTTextField(
                    value = fields.email,
                    onValueChange = { fields = fields.copy(email = it) },
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                )

                YTTextField(
                    value = fields.phone,
                    onValueChange = { fields = fields.copy(phone = it) },
                    label = "Phone",
                    keyboardType = KeyboardType.Phone,
                )

                YTTextField(
                    value = fields.website,
                    onValueChange = { fields = fields.copy(website = it) },
                    label = "Website",
                )

                YTDropdownField(
                    label = "You charge in",
                    selected = fields.currency,
                    options = CURRENCIES,
                    optionLabel = { it.code },
                    onSelect = { fields = fields.copy(currency = it) },
                    help = "Every price, total, and invoice is denominated in this.",
                )
            }

            YTSectionCard(title = "For invoices") {
                YTTextField(
                    value = fields.taxNumber,
                    onValueChange = { fields = fields.copy(taxNumber = it) },
                    label = "Tax registration number",
                    help =
                        "VAT, EIN, ABN, GST — whatever yours is called. In most places an " +
                            "invoice without it cannot be claimed against by your client.",
                )

                YTTextField(
                    value = fields.paymentInstructions,
                    onValueChange = { fields = fields.copy(paymentInstructions = it) },
                    label = "How to pay you",
                    singleLine = false,
                    help = "Bank details, a payment link — whatever a client needs to actually send the money.",
                )

                YTTextField(
                    value = fields.documentFooter,
                    onValueChange = { fields = fields.copy(documentFooter = it) },
                    label = "Footer",
                    singleLine = false,
                    imeAction = ImeAction.Done,
                    help = "Payment terms, a late payment notice, a company registration line.",
                )
            }

            YTButton(
                text = "Save",
                onClick = { onSave(fields) },
            )

            // What a client will notice is absent, said after saving rather than as a
            // wall of warnings on an empty form nobody has filled in yet.
            if (content.gaps.isNotEmpty()) {
                Text(
                    text = "Your invoices will go out with ${content.gaps.joinToString(", ")}.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )
            }

            content.savedNote?.let { note ->
                Text(
                    text = note,
                    style = YTTheme.typography.bodyMedium,
                    color = if (content.canIssueDocuments) YTTheme.colors.primary else YTTheme.colors.error,
                )
            }
        }
    }
}

/**
 * What synchronisation discarded, and what it kept instead.
 *
 * Placed above the studio's own details because it is the only thing on this screen that
 * is time-sensitive: the rest can be filled in whenever, and this is work that has already
 * been thrown away once.
 *
 * ADR 0008 decision 3 chose last-write-wins on the condition that the losing version stays
 * recoverable by whoever wrote it. Until this existed, the condition was not being met —
 * the payloads were being stored and shown to nobody, which the ADR itself called worse
 * than useless.
 */
@Composable
private fun ConflictsSection(
    conflicts: List<ConflictSummary>,
    onDismiss: (SyncConflictId) -> Unit,
) {
    val single = conflicts.size == 1

    YTSectionCard(
        title = if (single) "1 change was overwritten" else "${conflicts.size} changes were overwritten",
    ) {
        Text(
            text =
                if (single) {
                    "You edited this on two devices at once. The newer version was kept and the " +
                        "other was set aside — it is below, so you can put back anything that " +
                        "still matters."
                } else {
                    "You edited these on two devices at once. The newer versions were kept and the " +
                        "others were set aside — they are below, so you can put back anything that " +
                        "still matters."
                },
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )

        conflicts.forEach { conflict ->
            Column(verticalArrangement = Arrangement.spacedBy(YTTheme.spacing.small)) {
                Text(
                    text = "${conflict.what}, ${conflict.whenDetected}",
                    style = YTTheme.typography.titleSmall,
                    color = YTTheme.colors.onSurface,
                )

                if (conflict.isUnreadable) {
                    // Still listed. A version that will not render is still a version that
                    // was thrown away, and quietly dropping it is the exact failure this
                    // screen exists to prevent.
                    Text(
                        text = "The set-aside version could not be read back. Nothing else has been lost.",
                        style = YTTheme.typography.bodySmall,
                        color = YTTheme.colors.onSurfaceVariant,
                    )
                } else {
                    conflict.differences.forEach { difference ->
                        Text(
                            text = difference.label,
                            style = YTTheme.typography.labelMedium,
                            color = YTTheme.colors.onSurfaceVariant,
                        )
                        Text(
                            text = "Kept: ${difference.kept}",
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.onSurface,
                        )
                        Text(
                            text = "Set aside: ${difference.discarded}",
                            style = YTTheme.typography.bodyMedium,
                            color = YTTheme.colors.error,
                        )
                    }
                }

                YTButton(
                    text = "I have dealt with this",
                    onClick = { onDismiss(conflict.id) },
                )
            }
        }
    }
}

/**
 * The currencies offered.
 *
 * A short list rather than all of ISO 4217: these are the ones the money formatter has a
 * symbol for, and offering a hundred and eighty codes that render as "XPF 4,000.00" would
 * be a worse answer than a short list plus a request.
 */
private val CURRENCIES =
    listOf(
        CurrencyCode.USD,
        CurrencyCode.EUR,
        CurrencyCode.GBP,
        CurrencyCode.CAD,
        CurrencyCode.AUD,
        CurrencyCode.KES,
    )
