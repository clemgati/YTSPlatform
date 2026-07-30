package com.yellowtrack.platform.core.common.storage

/**
 * The browser has no filesystem to read.
 *
 * Reported as unsupported rather than as "nothing found": telling a studio its backups
 * have vanished because the page cannot see its drives would be the worst possible answer.
 */
typealias WebVolumeInspector = UnsupportedVolumeInspector
