package com.yellowtrack.platform.core.model.client

import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.contact.Contact
import kotlinx.serialization.Serializable

/**
 * A client account — who the studio does business with.
 *
 * An account, not a person. A wedding client is a couple; a commercial client is a
 * company whose brief-giver, approver, and payer are three different people. Modelling
 * the client as a single person makes all three of those unrepresentable, which is why
 * people belong to accounts through [ClientContact] rather than being the account.
 *
 * @param accountName how the account is addressed — "Sarah & Michael Johnson",
 *   "Harborline Coffee". Stored rather than derived, because the right rendering of a
 *   couple's name is a judgement call the studio should be able to make.
 */
@Serializable
data class Client(
    val id: ClientId,
    override val studioId: StudioId,
    val accountName: String,
    val accountType: ClientAccountType,
    val contacts: List<ClientContact> = emptyList(),
    val notes: String? = null,
    val tags: List<String> = emptyList(),
    override val audit: AuditMetadata,
) : StudioScoped {
    /** Falls back to the primary contact's name when no account name has been set. */
    val displayName: String
        get() = accountName.ifBlank { primaryContact?.displayName.orEmpty() }

    val primaryContact: Contact?
        get() =
            contacts.firstOrNull { it.role == ClientContactRole.Primary }?.contact
                ?: contacts.firstOrNull()?.contact

    /** Where an invoice should be sent, which is not always the primary contact. */
    val billingContact: Contact?
        get() =
            contacts.firstOrNull { it.role == ClientContactRole.Billing }?.contact
                ?: primaryContact

    fun contactsInRole(role: ClientContactRole): List<Contact> =
        contacts
            .filter {
                it.role == role
            }.map(ClientContact::contact)
}
