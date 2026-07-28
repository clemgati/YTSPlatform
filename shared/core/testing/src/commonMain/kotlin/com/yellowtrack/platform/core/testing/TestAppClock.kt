package com.yellowtrack.platform.core.testing

import com.yellowtrack.platform.core.common.time.AppClock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A clock tests can move.
 *
 * Almost everything in this domain is a function of "now" — session status, invoice
 * overdue state, lead response time — so tests need to control it rather than observe it.
 */
class TestAppClock(
    private var current: Instant = DEFAULT_NOW,
) : AppClock {
    override fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current += duration
    }

    fun setTo(instant: Instant) {
        current = instant
    }

    companion object {
        /** 2026-06-13T14:00:00Z — a Saturday afternoon, which is when weddings happen. */
        val DEFAULT_NOW: Instant = Instant.fromEpochMilliseconds(1_781_100_000_000)
    }
}
