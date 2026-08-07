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
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTSectionCard
import com.yellowtrack.platform.core.designsystem.component.YTTextButton
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
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
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onExport: () -> Unit,
    onDeleteAccount: (password: String, onRefused: (String) -> Unit) -> Unit,
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

            YTSectionCard(title = "Synchronisation") {
                Text(
                    text =
                        "Your work is kept on this device and copied to your other ones. It " +
                            "keeps working with no connection and catches up afterwards.",
                    style = YTTheme.typography.bodyMedium,
                    color = YTTheme.colors.onSurfaceVariant,
                )

                content.sync.lastResult?.let { result ->
                    Text(
                        text = result,
                        style = YTTheme.typography.bodyMedium,
                        color = if (content.sync.isFailure) YTTheme.colors.error else YTTheme.colors.onSurface,
                    )
                }

                if (content.sync.notReconciled.isNotEmpty()) {
                    // Deliberately specific about the number rather than "some": a studio
                    // deciding whether to keep working on one device needs to know how much
                    // is not travelling, and the server's own operator needs to know to
                    // deploy. Naming every table would be noise on a phone.
                    Text(
                        text =
                            "This server is older than the app and does not handle " +
                                "${content.sync.notReconciled.size} kinds of record. Everything " +
                                "else is syncing; those stay on this device until it is updated.",
                        style = YTTheme.typography.bodyMedium,
                        color = YTTheme.colors.error,
                    )
                }

                YTButton(
                    text = if (content.sync.isWorking) "Syncing…" else "Sync now",
                    onClick = onSyncNow,
                    enabled = !content.sync.isWorking,
                )
            }

            content.account?.let { account ->
                AccountSection(
                    account = account,
                    onSignOut = onSignOut,
                    onExport = onExport,
                    onDeleteAccount = onDeleteAccount,
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

@Composable
private fun AccountSection(
    account: AccountSummary,
    onSignOut: () -> Unit,
    onExport: () -> Unit,
    onDeleteAccount: (password: String, onRefused: (String) -> Unit) -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }

    YTSectionCard(title = "Account") {
        Text(
            text = "Signed in as ${account.email}, for ${account.studioName}.",
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurface,
        )

        Text(
            text =
                if (account.isHardwareBacked) {
                    "Signing out removes this device's access. Your work stays on it, and " +
                        "goes up the next time you sign in."
                } else {
                    // The same warning the sign-in screen gives, repeated where the remedy
                    // is. Telling somebody to sign out on a screen that cannot sign them out
                    // is what this section was added to fix.
                    "This device cannot store your sign-in securely, so sign out when you " +
                        "have finished. Your work stays on it, and goes up the next time you " +
                        "sign in."
                },
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )

        YTButton(
            text = "Sign out",
            onClick = onSignOut,
        )
    }

    YTSectionCard(title = "Your data") {
        Text(
            text =
                "Everything this studio has — clients, bookings, quotes, contracts, invoices, " +
                    "payments and the rest — as one file you keep.",
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )

        YTButton(
            text = "Download everything",
            onClick = onExport,
        )

        Text(
            text =
                "Deleting removes this studio and everything in it, on every device. For 30 " +
                    "days you can bring it back by signing in again; after that it cannot be " +
                    "recovered by anybody.",
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )

        // Deliberately below the download, and deliberately in the same card. The moment
        // somebody reads "cannot be recovered" is the moment to be able to take a copy, and
        // a copy offered on a different screen is advice rather than an option.
        YTTextButton(
            text = "Delete this studio",
            onClick = { confirmingDelete = true },
            contentColor = YTTheme.colors.error,
        )
    }

    if (confirmingDelete) {
        DeleteStudioDialog(
            studioName = account.studioName,
            onDismiss = { confirmingDelete = false },
            onConfirm = onDeleteAccount,
        )
    }
}

/**
 * The last thing between a studio and its own deletion.
 *
 * Asks for the password rather than for the word DELETE. The server requires it either way —
 * a token is what a borrowed laptop already has — so typing it here is the same act, and
 * a confirmation somebody can satisfy without knowing the password is not one.
 */
@Composable
internal fun DeleteStudioDialog(
    studioName: String,
    onDismiss: () -> Unit,
    onConfirm: (password: String, onRefused: (String) -> Unit) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var refusal by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }

    YTFormDialog(
        title = "Delete $studioName?",
        confirmLabel = "Delete everything",
        confirmEnabled = password.isNotBlank() && !isWorking,
        isDestructive = true,
        onConfirm = {
            isWorking = true
            refusal = null
            onConfirm(password) { reason ->
                // Back to the form rather than closed. A dialog that shuts on a wrong
                // password looks exactly like one that worked.
                refusal = reason
                isWorking = false
            }
        },
        onDismiss = onDismiss,
    ) {
        Text(
            text =
                "This removes every client, booking, document and payment belonging to " +
                    "$studioName, on every device. You will be signed out everywhere. For 30 " +
                    "days, signing in again offers it back; after that it is gone.",
            style = YTTheme.typography.bodyMedium,
            color = YTTheme.colors.onSurfaceVariant,
        )

        YTTextField(
            value = password,
            onValueChange = {
                password = it
                refusal = null
            },
            label = "Your password",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            errorMessage = refusal,
            help = "Asked for again because a signed-in device alone is not enough to do this.",
        )
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
