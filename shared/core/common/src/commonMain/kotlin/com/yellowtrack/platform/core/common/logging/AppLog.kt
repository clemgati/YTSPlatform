package com.yellowtrack.platform.core.common.logging

/**
 * Somewhere for a failure to go, which this application did not have.
 *
 * Written after a device stopped advancing its sync cursor and did so for forty-one hours
 * without a trace. Every ingredient of that diagnosis came from the database — comparing a
 * local cursor against `server_seq`, reading conflict payloads to find a row invented a
 * quarter of a second before it was pushed. None of it came from a log, because there was no
 * log: the desktop build prints `No SLF4J providers were found` at startup, so even Ktor's
 * own account of a failed request went nowhere.
 *
 * Deliberately tiny. Levels, tags, formatters and a configuration file are all things to
 * regret later; what was missing was **the throwable**, and one function that cannot lose it
 * is worth more than a logging framework nobody wires up.
 *
 * Not a substitute for [com.yellowtrack.platform.core.data.sync.SyncStatus] and the messages
 * on screen. Those are for the studio, and say what to do about it. This is for whoever has
 * to work out why, and keeps the stack trace that a message deliberately throws away.
 */
expect fun logFailure(
    /** Where it happened, in words that would be searched for. Not a class name. */
    where: String,
    error: Throwable,
)
