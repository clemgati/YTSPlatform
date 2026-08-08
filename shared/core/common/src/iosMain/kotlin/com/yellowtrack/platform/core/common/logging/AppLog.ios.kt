package com.yellowtrack.platform.core.common.logging

import platform.Foundation.NSLog

/**
 * `NSLog` rather than `println`, so it reaches Console and a device log rather than only an
 * attached Xcode session. A fault that only appears while a developer is watching is the
 * kind this exists to catch.
 */
actual fun logFailure(
    where: String,
    error: Throwable,
) {
    NSLog("[yellowtrack] %s: %s", where, error.stackTraceToString())
}
