package com.yellowtrack.platform.feature.clients.presentation.list.model

import com.yellowtrack.platform.core.model.client.ClientAccountType

/**
 * What the client form collected.
 *
 * An account and its first contact are captured together because they arrive together: an
 * enquiry gives a name and an email in the same breath, and a client account with nobody
 * attached to it cannot be emailed, invoiced, or rung on the morning of a shoot.
 *
 * Either [accountName] or a contact name will do. `Client.displayName` already falls back
 * from one to the other, so demanding both would be friction the model does not need —
 * "Harbourline Coffee" needs no person yet, and "Ada Okafor" needs no separate account
 * name.
 */
internal data class NewClient(
    val accountName: String,
    val accountType: ClientAccountType,
    val contactFirstName: String,
    val contactLastName: String,
    val company: String,
    val email: String,
    val phone: String,
    val notes: String,
) {
    val hasName: Boolean
        get() = accountName.isNotBlank() || contactFirstName.isNotBlank() || contactLastName.isNotBlank()

    val hasContact: Boolean
        get() =
            contactFirstName.isNotBlank() ||
                contactLastName.isNotBlank() ||
                email.isNotBlank() ||
                phone.isNotBlank()
}
