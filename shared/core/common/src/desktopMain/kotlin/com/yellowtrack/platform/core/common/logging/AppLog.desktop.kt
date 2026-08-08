package com.yellowtrack.platform.core.common.logging

/**
 * Standard error, which the packaged application does not show and a terminal does.
 *
 * That asymmetry is the point rather than a shortcoming: running
 * `Yellow Track.app/Contents/MacOS/Yellow Track` from a terminal is already the way faults in
 * the desktop build get diagnosed, and this puts sync failures in the same place as the
 * startup errors that habit was formed on.
 */
actual fun logFailure(
    where: String,
    error: Throwable,
) {
    System.err.println("[yellowtrack] $where")
    error.printStackTrace()
}
