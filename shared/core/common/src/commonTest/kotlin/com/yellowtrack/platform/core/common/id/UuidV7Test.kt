package com.yellowtrack.platform.core.common.id

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
class UuidV7Test {
    @Test
    fun `sets the version and variant bits required by RFC 9562`() {
        val uuid = uuidV7()
        val text = uuid.toString()

        // Version nibble is the first character of the third group.
        assertEquals('7', text[14], "expected version 7 in $text")

        // Variant is 0b10xx, rendered as 8, 9, a, or b.
        assertTrue(text[19] in "89ab", "expected RFC 9562 variant in $text")
    }

    @Test
    fun `sorts lexicographically by creation time`() {
        val random = Random(seed = 42)

        val earlier = uuidV7(Instant.fromEpochMilliseconds(1_700_000_000_000), random)
        val later = uuidV7(Instant.fromEpochMilliseconds(1_700_000_001_000), random)
        val muchLater = uuidV7(Instant.fromEpochMilliseconds(1_900_000_000_000), random)

        val sorted = listOf(muchLater, earlier, later).map(Any::toString).sorted()

        assertEquals(
            listOf(earlier.toString(), later.toString(), muchLater.toString()),
            sorted,
        )
    }

    @Test
    fun `produces distinct identifiers within the same millisecond`() {
        val timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val generated = List(1_000) { uuidV7(timestamp) }

        assertEquals(generated.size, generated.toSet().size)
    }
}
