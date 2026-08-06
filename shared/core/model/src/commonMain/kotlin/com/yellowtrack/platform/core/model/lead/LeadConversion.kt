package com.yellowtrack.platform.core.model.lead

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contact.ContactMethod
import kotlin.time.Instant

/**
 * Turning an enquiry into a client, without retyping what it already said.
 *
 * A studio that has just won a job has an enquiry holding the person's name, address and
 * telephone number, and has to type all of it again into a client form. Doing that by hand is
 * slow and, worse, produces a client record that no longer matches the enquiry it came from —
 * so nothing can say afterwards which enquiries turned into work.
 *
 * `Lead.convertedClientId` has existed since the schema was first written and nothing has ever
 * set it. This is what sets it.
 */
object LeadConversion {
    /**
     * The client this enquiry describes.
     *
     * A person rather than a company, because an enquiry carries one name and no company
     * field. A studio shooting for a business corrects that on the client's own page, which is
     * one edit — where getting it wrong the other way means an individual's record permanently
     * shaped like an organisation's.
     */
    fun clientFrom(
        lead: Lead,
        clientId: ClientId,
        contactId: ContactId,
        now: Instant,
    ): Client {
        val audit = AuditMetadata(createdAt = now, updatedAt = now)

        return Client(
            id = clientId,
            studioId = lead.studioId,
            accountName = lead.name.trim(),
            accountType = ClientAccountType.Individual,
            contacts =
                listOf(
                    ClientContact(
                        contact =
                            Contact(
                                id = contactId,
                                studioId = lead.studioId,
                                firstName = firstNameOf(lead.name),
                                lastName = lastNameOf(lead.name),
                                emails =
                                    lead.email
                                        ?.trim()
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.let {
                                            listOf(
                                                ContactMethod(it),
                                            )
                                        }.orEmpty(),
                                phones =
                                    lead.phone
                                        ?.trim()
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.let {
                                            listOf(
                                                ContactMethod(it),
                                            )
                                        }.orEmpty(),
                                audit = audit,
                            ),
                        role = ClientContactRole.Primary,
                    ),
                ),
            // Carried rather than dropped. What somebody wrote when the enquiry arrived is
            // usually the only record of what they actually asked for.
            notes = lead.notes?.trim()?.takeIf { it.isNotEmpty() },
            audit = audit,
        )
    }

    /**
     * The enquiry, marked won and pointed at the client it became.
     *
     * Both together, because either alone is a half-truth: a won enquiry with no client says
     * work was won and names nothing, and a link on an enquiry still marked New says the
     * opposite of what happened.
     */
    fun converted(
        lead: Lead,
        clientId: ClientId,
        now: Instant,
    ): Lead =
        lead.copy(
            status = LeadStatus.Won,
            convertedClientId = clientId,
            // Winning one is answering it. An enquiry that produced a client without ever
            // being marked replied to would otherwise sit on the response-time list forever.
            firstResponseAt = lead.firstResponseAt ?: now,
            audit = lead.audit.copy(updatedAt = now, version = lead.audit.version + 1),
        )

    /**
     * Everything before the last space, or the whole name.
     *
     * Deliberately crude, and the crudeness is the point. Names do not split reliably — "Ada
     * Okafor" and "Priya & Tom Sandhu" and "van der Berg" all break a cleverer rule in
     * different ways — so this puts the whole name somewhere findable rather than guessing
     * well most of the time and mangling the rest. The studio corrects it in one edit.
     */
    private fun firstNameOf(name: String): String {
        val trimmed = name.trim()
        val cut = trimmed.lastIndexOf(' ')
        return if (cut <= 0) trimmed else trimmed.substring(0, cut)
    }

    private fun lastNameOf(name: String): String {
        val trimmed = name.trim()
        val cut = trimmed.lastIndexOf(' ')
        return if (cut <= 0) "" else trimmed.substring(cut + 1)
    }
}
