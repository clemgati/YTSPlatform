package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientContact
import com.yellowtrack.platform.core.model.client.ClientContactRole
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contact.Contact
import com.yellowtrack.platform.core.model.contact.ContactId
import com.yellowtrack.platform.core.model.contact.ContactMethod
import com.yellowtrack.platform.feature.ledger.presentation.mapper.toSendTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * Where a document is offered to be sent.
 *
 * The rule this exists for is billing over primary: on a wedding the person who booked and the
 * person who pays are often different people, and an invoice prefilled to the wrong one of
 * those is an awkward conversation the studio did not choose.
 */
class SendToTest {
    @Test
    fun `prefers the billing contact over the one who booked`() {
        val client =
            client(
                contact("Priya", "priya@sandhu.example", ClientContactRole.Primary),
                contact("Tom", "tom@sandhu.example", ClientContactRole.Billing),
            )

        val sendTo = assertNotNull(client.toSendTo())

        assertEquals("tom@sandhu.example", sendTo.email, "the invoice goes to whoever pays it")
        assertEquals("Tom", sendTo.name, "the studio has to see who, not only where")
    }

    @Test
    fun `falls back to the primary contact when nobody is named for billing`() {
        val client = client(contact("Priya", "priya@sandhu.example", ClientContactRole.Primary))

        assertEquals("priya@sandhu.example", assertNotNull(client.toSendTo()).email)
    }

    /** An empty box is the honest prompt. A name with nothing behind it is not. */
    @Test
    fun `offers nothing when the client has no address on file`() {
        assertNull(client(contact("Priya", null, ClientContactRole.Primary)).toSendTo())
        assertNull(client(contact("Priya", "   ", ClientContactRole.Primary)).toSendTo())
        assertNull(client().toSendTo())
    }

    private fun client(vararg contacts: ClientContact) =
        Client(
            id = ClientId("client-1"),
            studioId = StudioId("studio-1"),
            accountName = "Priya & Tom Sandhu",
            accountType = ClientAccountType.Individual,
            contacts = contacts.toList(),
            audit = audit(),
        )

    private fun contact(
        name: String,
        email: String?,
        role: ClientContactRole,
    ) = ClientContact(
        contact =
            Contact(
                id = ContactId("contact-$name"),
                studioId = StudioId("studio-1"),
                firstName = name,
                lastName = "",
                emails = email?.let { listOf(ContactMethod(it)) }.orEmpty(),
                audit = audit(),
            ),
        role = role,
    )

    private fun audit() =
        AuditMetadata(
            createdAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
            updatedAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
        )
}
