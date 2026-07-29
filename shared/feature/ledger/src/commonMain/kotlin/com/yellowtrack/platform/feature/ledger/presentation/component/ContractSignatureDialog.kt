package com.yellowtrack.platform.feature.ledger.presentation.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractItem
import com.yellowtrack.platform.feature.ledger.presentation.model.ContractSignature
import kotlinx.datetime.LocalDate

/**
 * Records that a contract was signed, by whom, and when.
 *
 * The date is asked for rather than taken from the clock: contracts are signed on paper
 * and entered days later, and the date a client became bound is the date that decides
 * whether a cancellation falls inside the notice period.
 *
 * A signature does not close the row. Where a retainer is owed, the booking stays on the
 * Ledger until that money arrives, because that is the point at which the date is held.
 */
@Composable
internal fun ContractSignatureDialog(
    contract: ContractItem,
    today: LocalDate,
    onSave: (ContractSignature) -> Unit,
    onDismiss: () -> Unit,
) {
    var signerName by remember { mutableStateOf(contract.clientName) }
    var signerEmail by remember { mutableStateOf("") }
    var signedOn by remember { mutableStateOf(today.toString()) }

    val signedOnValid = runCatching { LocalDate.parse(signedOn) }.isSuccess

    YTFormDialog(
        title = "Record a signature",
        confirmLabel = "Signed",
        supportingText = contract.title,
        confirmEnabled = signerName.isNotBlank() && signedOnValid,
        onConfirm = {
            onSave(
                ContractSignature(
                    contractId = contract.id,
                    signerName = signerName.trim(),
                    signerEmail = signerEmail.trim().ifBlank { null },
                    signedOn = signedOn.trim(),
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        YTTextField(
            value = signerName,
            onValueChange = { signerName = it },
            label = "Signed by",
            help = "A contract signed by nobody in particular is evidence of nothing.",
        )

        YTTextField(
            value = signerEmail,
            onValueChange = { signerEmail = it },
            label = "Their email",
            keyboardType = KeyboardType.Email,
        )

        YTTextField(
            value = signedOn,
            onValueChange = { signedOn = it },
            label = "Date signed",
            placeholder = today.toString(),
            imeAction = ImeAction.Done,
            errorMessage = if (!signedOnValid) "Use the form $today" else null,
        )

        if (contract.retainer != null) {
            Text(
                text = "${contract.retainer} retainer is still outstanding — the date is not held until it is paid.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}
