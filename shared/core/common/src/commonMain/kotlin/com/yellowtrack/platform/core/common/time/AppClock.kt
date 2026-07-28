package com.yellowtrack.platform.core.common.time

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The source of "now" for the application.
 *
 * Injected rather than called statically so that tests can control time. Almost every
 * entity in this domain is timestamped, and a great deal of behaviour — session status,
 * invoice overdue state, lead response time — is a function of the current instant.
 */
fun interface AppClock {
    fun now(): Instant

    companion object {
        val System: AppClock = AppClock { Clock.System.now() }
    }
}
