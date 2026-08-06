package com.yellowtrack.platform.core.model.lead

import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.contact.ContactId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LeadConversionTest {
    private val now = Instant.fromEpochMilliseconds(1_781_500_000_000)

    @Test
    fun `carries what the enquiry already said`() {
        val client = LeadConversion.clientFrom(lead(), ClientId("client-1"), ContactId("contact-1"), now)

        assertEquals("Ada Okafor", client.accountName)
        assertEquals(ClientAccountType.Individual, client.accountType)
        assertEquals("Wants black and white.", client.notes)

        val contact = assertNotNull(client.contacts.singleOrNull()).contact
        assertEquals("ada@okafor.example", contact.emails.single().value)
        assertEquals("+44 7700 900123", contact.phones.single().value)
    }

    @Test
    fun `leaves out contact methods the enquiry never had`() {
        val client =
            LeadConversion.clientFrom(
                lead().copy(email = null, phone = "   "),
                ClientId("client-1"),
                ContactId("contact-1"),
                now,
            )

        val contact = client.contacts.single().contact
        assertTrue(contact.emails.isEmpty())
        assertTrue(contact.phones.isEmpty(), "whitespace is not a telephone number")
    }

    /**
     * Names do not split reliably, so the rule is crude on purpose: the whole name ends up
     * somewhere findable rather than being guessed at well most of the time and mangled the
     * rest. A studio corrects it in one edit.
     */
    @Test
    fun `puts the whole name somewhere findable`() {
        fun split(name: String) =
            LeadConversion
                .clientFrom(lead().copy(name = name), ClientId("c"), ContactId("k"), now)
                .contacts
                .single()
                .contact
                .let { it.firstName to it.lastName }

        assertEquals("Ada" to "Okafor", split("Ada Okafor"))
        assertEquals("Priya & Tom" to "Sandhu", split("Priya & Tom Sandhu"))
        assertEquals("Cher" to "", split("Cher"))
        assertEquals("Ada" to "Okafor", split("  Ada Okafor  "))
    }

    @Test
    fun `marks the enquiry won and points it at the client`() {
        val converted = LeadConversion.converted(lead(), ClientId("client-1"), now)

        assertEquals(LeadStatus.Won, converted.status)
        assertEquals(ClientId("client-1"), converted.convertedClientId)
        assertEquals(2, converted.audit.version, "the change has to travel to the studio's other devices")
    }

    /**
     * An enquiry that produced a client but was never marked replied to would sit on the
     * response-time list forever, which is the list a studio is meant to empty.
     */
    @Test
    fun `winning one counts as answering it`() {
        val converted = LeadConversion.converted(lead().copy(firstResponseAt = null), ClientId("c"), now)

        assertEquals(now, converted.firstResponseAt)
    }

    @Test
    fun `does not overwrite when it was answered earlier`() {
        val replied = Instant.fromEpochMilliseconds(1_781_100_000_000)
        val converted = LeadConversion.converted(lead().copy(firstResponseAt = replied), ClientId("c"), now)

        assertEquals(replied, converted.firstResponseAt, "the response time is a measurement, not a formality")
    }

    private fun lead() =
        Lead(
            id = LeadId("lead-1"),
            studioId = StudioId("studio-1"),
            name = "Ada Okafor",
            source = LeadSource.ClientReferral,
            status = LeadStatus.Contacted,
            receivedAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
            email = "ada@okafor.example",
            phone = "+44 7700 900123",
            notes = "Wants black and white.",
            audit =
                AuditMetadata(
                    createdAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
                    updatedAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
                ),
        )
}
