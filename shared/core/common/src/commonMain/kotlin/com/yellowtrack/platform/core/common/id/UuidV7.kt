package com.yellowtrack.platform.core.common.id

import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Generates a UUID version 7 — a 48-bit Unix millisecond timestamp followed by random bits.
 *
 * Version 7 rather than version 4 because the leading timestamp makes identifiers
 * lexicographically sortable by creation time. That keeps B-tree index inserts local
 * rather than scattered, which matters once a studio has years of sessions.
 *
 * Identifiers are generated on the client, never by the database. A photographer creating
 * a session in a venue with no signal must produce a permanent, valid identifier with no
 * server round trip; sequences and auto-increment columns cannot do that.
 *
 * Layout (RFC 9562):
 * ```
 * unix_ts_ms (48) | ver (4) = 7 | rand_a (12) | var (2) = 0b10 | rand_b (62)
 * ```
 */
@OptIn(ExperimentalUuidApi::class)
fun uuidV7(
    timestamp: Instant = Clock.System.now(),
    random: Random = Random.Default,
): Uuid {
    val unixMillis = timestamp.toEpochMilliseconds() and TIMESTAMP_MASK

    val mostSignificantBits =
        (unixMillis shl 16) or
            (VERSION_7 shl 12) or
            random.nextInt(RANDOM_A_BOUND).toLong()

    val leastSignificantBits =
        (random.nextLong() and RANDOM_B_MASK) or VARIANT_RFC_9562

    return Uuid.fromLongs(mostSignificantBits, leastSignificantBits)
}

private const val TIMESTAMP_MASK = 0x0000_FFFF_FFFF_FFFFL
private const val VERSION_7 = 0x7L
private const val RANDOM_A_BOUND = 0x1000
private const val RANDOM_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
private const val VARIANT_RFC_9562 = 0x2L shl 62
