package com.yellowtrack.platform.feature.ledger

import com.yellowtrack.platform.feature.ledger.presentation.mapper.INVOICE_PREFIX
import com.yellowtrack.platform.feature.ledger.presentation.mapper.nextNumber
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentNumberingTest {
    @Test
    fun `the first document starts the sequence`() {
        assertEquals("INV-001", nextNumber(INVOICE_PREFIX, emptyList()))
    }

    @Test
    fun `numbering continues from the highest used, not the count`() {
        // Four rows but the sequence has reached 12; suggesting INV-005 would reuse a
        // number a client has already been sent.
        assertEquals(
            "INV-013",
            nextNumber(INVOICE_PREFIX, listOf("INV-012", "INV-003", "INV-001", "INV-002")),
        )
    }

    @Test
    fun `a gap left by a deleted document is not filled`() {
        assertEquals("INV-009", nextNumber(INVOICE_PREFIX, listOf("INV-001", "INV-008")))
    }

    @Test
    fun `a studio's own scheme is ignored rather than renumbered`() {
        assertEquals(
            "INV-001",
            nextNumber(INVOICE_PREFIX, listOf("2026-JOHNSON-1", "SJP/044")),
        )
    }

    @Test
    fun `a wider sequence keeps its width`() {
        assertEquals("INV-10000", nextNumber(INVOICE_PREFIX, listOf("INV-09999")))
    }

    @Test
    fun `a sequence rolling past its padding widens rather than truncates`() {
        assertEquals("INV-1000", nextNumber(INVOICE_PREFIX, listOf("INV-999")))
    }

    @Test
    fun `an unparseable suffix does not stop the rest of the sequence`() {
        assertEquals(
            "INV-006",
            nextNumber(INVOICE_PREFIX, listOf("INV-005", "INV-draft", "INV-")),
        )
    }
}
