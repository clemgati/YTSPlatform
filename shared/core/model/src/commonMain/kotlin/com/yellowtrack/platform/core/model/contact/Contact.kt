package com.yellowtrack.platform.core.model.contact

import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import kotlinx.serialization.Serializable

/**
 * A person.
 *
 * Deliberately separate from `Client`: a wedding has two decision-makers and often a
 * planner, a commercial job has a brief-giver and a separate accounts-payable address,
 * and the planner who refers work is one person across many client accounts.
 */
@Serializable
data class Contact(
    val id: ContactId,
    override val studioId: StudioId,
    val firstName: String,
    val lastName: String,
    val company: String? = null,
    val jobTitle: String? = null,
    val emails: List<ContactMethod> = emptyList(),
    val phones: List<ContactMethod> = emptyList(),
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    val displayName: String
        get() =
            listOf(firstName, lastName)
                .filter(String::isNotBlank)
                .joinToString(" ")
                .ifBlank { company.orEmpty() }

    /** The number to text on shoot day. */
    val primaryPhone: String?
        get() =
            phones.firstOrNull { it.label == ContactMethodLabel.Primary }?.value
                ?: phones.firstOrNull()?.value

    val primaryEmail: String?
        get() =
            emails.firstOrNull { it.label == ContactMethodLabel.Primary }?.value
                ?: emails.firstOrNull()?.value
}
