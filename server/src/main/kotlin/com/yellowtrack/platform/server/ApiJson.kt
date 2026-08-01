package com.yellowtrack.platform.server

import kotlinx.serialization.json.Json

/**
 * The JSON the API speaks.
 *
 * Configured deliberately rather than by default, because both ends of this wire are
 * compiled from the same `core:model` (ADR 0007) and the settings decide what happens
 * when they are briefly *not* the same version — during a rolling deploy, or on a phone
 * that has not been updated in a month.
 */
val apiJson: Json =
    Json {
        // A client on an older build must not fail on a field it has never heard of. The
        // shared model makes drift a compile error at build time; this makes it survivable
        // at run time, in the window where a new server is talking to an old client.
        ignoreUnknownKeys = true

        // Defaults are written out rather than omitted. A field left off the wire is filled
        // in from the *reader's* default, so if the two builds disagree about what a
        // default is, the value silently changes in transit. Sending it explicitly means
        // the reader gets what the writer meant.
        encodeDefaults = true

        // Null is a value here, not an absence: a null `deletedAt` is a live row and a null
        // `verifiedAt` is a backup nobody has opened. Both must survive the round trip
        // rather than being dropped and re-defaulted.
        explicitNulls = true
    }
