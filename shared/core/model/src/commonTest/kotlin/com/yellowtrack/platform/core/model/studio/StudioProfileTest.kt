package com.yellowtrack.platform.core.model.studio

import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What has to be true before a document can carry a studio's name.
 *
 * The distinction the model draws is between what *stops* a document going out and what a
 * client will merely notice is missing. Only the name does the first.
 */
class StudioProfileTest {
    private val now = Instant.fromEpochMilliseconds(1_781_100_000_000)

    private fun profile(
        name: String = "Yellow Track Studios",
        address: String? = "12 Harbour Road\nFalmouth",
        email: String? = "hello@yellowtrack.example",
        phone: String? = "07700 900000",
        taxNumber: String? = "GB123456789",
        paymentInstructions: String? = "Bank transfer, sort 00-00-00, account 12345678",
    ) = StudioProfile(
        id = StudioProfileId.new(),
        studioId = StudioId("studio-1"),
        name = name,
        address = address,
        email = email,
        phone = phone,
        taxNumber = taxNumber,
        paymentInstructions = paymentInstructions,
        audit = AuditMetadata.createdAt(now),
    )

    @Test
    fun `a studio with a name can send documents`() {
        assertTrue(profile().canIssueDocuments)
    }

    @Test
    fun `a nameless studio cannot`() {
        assertFalse(
            profile(name = "").canIssueDocuments,
            "the client cannot tell who the invoice is from, and nobody finds out until the money does not arrive",
        )
    }

    @Test
    fun `a name of spaces is not a name`() {
        assertFalse(profile(name = "   ").canIssueDocuments)
    }

    @Test
    fun `a fully filled profile reports nothing missing`() {
        assertEquals(emptyList(), profile().documentGaps)
    }

    @Test
    fun `the gaps a client will notice are named`() {
        val gaps = profile(taxNumber = null, paymentInstructions = null).documentGaps

        assertEquals(listOf("no tax registration number", "no payment instructions"), gaps)
    }

    @Test
    fun `an invoice with no way to pay it is the expensive omission`() {
        assertTrue(profile(paymentInstructions = null).documentGaps.contains("no payment instructions"))
    }

    @Test
    fun `either an email or a phone counts as reachable`() {
        assertFalse(profile(phone = null).documentGaps.contains("no way to reach you"))
        assertFalse(profile(email = null).documentGaps.contains("no way to reach you"))
        assertTrue(profile(email = null, phone = null).documentGaps.contains("no way to reach you"))
    }

    @Test
    fun `a missing field and an empty one are the same absence`() {
        assertEquals(
            profile(address = null).documentGaps,
            profile(address = "").documentGaps,
            "otherwise a blank form field would read as a filled-in address",
        )
    }

    @Test
    fun `a studio starts with a profile it can open a form on`() {
        val empty = StudioProfile.empty(StudioId("studio-1"), AuditMetadata.createdAt(now))

        assertFalse(empty.canIssueDocuments)
        assertEquals(4, empty.documentGaps.size, "everything is missing, and it says so")
    }
}
