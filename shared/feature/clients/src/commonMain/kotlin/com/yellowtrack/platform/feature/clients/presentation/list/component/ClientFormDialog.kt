package com.yellowtrack.platform.feature.clients.presentation.list.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.yellowtrack.platform.core.designsystem.component.YTDropdownField
import com.yellowtrack.platform.core.designsystem.component.YTFormDialog
import com.yellowtrack.platform.core.designsystem.component.YTTextField
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.feature.clients.presentation.list.model.NewClient

/**
 * Takes on a client account and the first person attached to it.
 *
 * The account type is asked for first because it changes what the rest of the form means:
 * a couple is addressed by both names, a company is billed to the organisation rather than
 * to whoever briefed the job. The field carries an explanation for each option rather than
 * leaving the studio to infer it from four bare words.
 *
 * Only a name is required, and either kind will do — an account name for a company, a
 * person's name for an individual. Everything else can arrive later, because an enquiry
 * rarely arrives complete and a client that cannot be saved until it is complete is a
 * client kept in someone's inbox instead.
 */
@Composable
internal fun ClientFormDialog(
    onSave: (NewClient) -> Unit,
    onDismiss: () -> Unit,
) {
    var accountType by remember { mutableStateOf(ClientAccountType.Individual) }
    var accountName by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val form =
        NewClient(
            accountName = accountName,
            accountType = accountType,
            contactFirstName = firstName,
            contactLastName = lastName,
            company = company,
            email = email,
            phone = phone,
            notes = notes,
        )

    YTFormDialog(
        title = "Add a client",
        confirmLabel = "Save",
        confirmEnabled = form.hasName,
        onConfirm = { onSave(form) },
        onDismiss = onDismiss,
    ) {
        YTDropdownField(
            label = "Account type",
            selected = accountType,
            options = ClientAccountType.entries,
            optionLabel = { it.name },
            onSelect = { accountType = it },
            optionDescription = { it.explanation },
        )

        YTTextField(
            value = accountName,
            onValueChange = { accountName = it },
            label = "Account name",
            placeholder = accountType.accountNamePlaceholder,
            help = "How this account is addressed. Leave it blank to use the contact's name.",
        )

        YTTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = "First name",
        )

        YTTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = "Last name",
        )

        YTTextField(
            value = company,
            onValueChange = { company = it },
            label = "Company",
        )

        YTTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = KeyboardType.Email,
        )

        YTTextField(
            value = phone,
            onValueChange = { phone = it },
            label = "Phone",
            keyboardType = KeyboardType.Phone,
            help = "The number to text on shoot day.",
        )

        YTTextField(
            value = notes,
            onValueChange = { notes = it },
            label = "Notes",
            singleLine = false,
            imeAction = ImeAction.Done,
        )

        if (!form.hasContact) {
            Text(
                text = "With nobody attached, there is no one to email, invoice, or ring on the day.",
                style = YTTheme.typography.bodySmall,
                color = YTTheme.colors.onSurfaceVariant,
            )
        }
    }
}

private val ClientAccountType.explanation: String
    get() =
        when (this) {
            ClientAccountType.Individual -> "One person. Most portrait and headshot work."
            ClientAccountType.Couple -> "Two people of equal standing. The default for weddings."
            ClientAccountType.Company -> "Billed to the organisation rather than to a person."
            ClientAccountType.Agency -> "Books repeatedly on behalf of others."
        }

private val ClientAccountType.accountNamePlaceholder: String
    get() =
        when (this) {
            ClientAccountType.Individual -> "Ada Okafor"
            ClientAccountType.Couple -> "Sarah & Michael Johnson"
            ClientAccountType.Company -> "Harbourline Coffee"
            ClientAccountType.Agency -> "Northgate Talent"
        }
