package com.yellowtrack.platform.core.model.lead

import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class EnquiryOutcomesTest {
    @Test
    fun `counts what became of each enquiry`() {
        val outcomes =
            listOf(
                converted(),
                converted(),
                converted(),
                lost(),
                open(),
            ).outcomes()

        assertEquals(5, outcomes.total)
        assertEquals(3, outcomes.converted)
        assertEquals(1, outcomes.lost)
        assertEquals(1, outcomes.open)
    }

    /**
     * The reason there are three numbers. An enquiry that arrived this morning has not failed,
     * and counting it as one would make the rate move every time the studio got work.
     */
    @Test
    fun `the rate is measured over settled enquiries only`() {
        val outcomes = listOf(converted(), lost(), open(), open(), open()).outcomes()

        assertEquals(2, outcomes.settled)
        assertEquals(50, outcomes.conversionRate, "one of two settled, whatever else is in flight")
        assertEquals(5, outcomes.total, "the open ones are still reported, just not as failures")
    }

    /** A studio in its first week has not converted 0% of anything. */
    @Test
    fun `says nothing rather than zero when nothing has settled`() {
        assertNull(listOf(open(), open()).outcomes().conversionRate)
        assertNull(emptyList<Lead>().outcomes().conversionRate)
    }

    /**
     * Won is a label somebody can set; a client is a fact. An enquiry marked won with nothing
     * to show for it stays open, where it may still be noticed and finished.
     */
    @Test
    fun `won without a client is not counted as converted`() {
        val outcomes = listOf(lead(status = LeadStatus.Won, clientId = null)).outcomes()

        assertEquals(0, outcomes.converted)
        assertEquals(0, outcomes.lost)
        assertEquals(1, outcomes.open)
    }

    @Test
    fun `the example from the request`() {
        val outcomes =
            (List(150) { converted() } + List(38) { lost() } + List(12) { open() }).outcomes()

        assertEquals(200, outcomes.total)
        assertEquals(150, outcomes.converted)
        assertEquals(38, outcomes.lost)
        assertEquals(12, outcomes.open)
        assertEquals(79, outcomes.conversionRate, "150 of 188 settled, not 150 of 200")
    }

    private fun converted() = lead(status = LeadStatus.Won, clientId = ClientId("client-1"))

    private fun lost() = lead(status = LeadStatus.Lost, clientId = null)

    private fun open() = lead(status = LeadStatus.New, clientId = null)

    private fun lead(
        status: LeadStatus,
        clientId: ClientId?,
    ) = Lead(
        id = LeadId("lead-1"),
        studioId = StudioId("studio-1"),
        name = "Ada Okafor",
        source = LeadSource.ClientReferral,
        status = status,
        receivedAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
        convertedClientId = clientId,
        audit =
            AuditMetadata(
                createdAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
                updatedAt = Instant.fromEpochMilliseconds(1_781_000_000_000),
            ),
    )
}
