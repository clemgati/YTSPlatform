package com.yellowtrack.platform.core.common.logging

import org.slf4j.LoggerFactory

/**
 * Through SLF4J rather than straight to standard error.
 *
 * Writing to `System.err` directly was the first attempt and it was wrong for exactly the
 * reason this work exists: standard error is read by somebody who launched from a terminal
 * and was already suspicious, and an application opened from the Finder discards it. The
 * whole point was to catch the *next* forty-one hours of silence without anybody watching.
 *
 * Going through the logger means one configuration decides where this lands — the desktop
 * build sends it to the console *and* to a rolling file beside the database. Found by
 * building the file appender, running the application, and noticing the file stayed empty.
 */
private val logger = LoggerFactory.getLogger("yellowtrack")

actual fun logFailure(
    where: String,
    error: Throwable,
) {
    logger.error(where, error)
}
