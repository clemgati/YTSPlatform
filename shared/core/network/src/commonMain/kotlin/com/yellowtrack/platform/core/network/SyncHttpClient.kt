package com.yellowtrack.platform.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The JSON the device speaks, configured to match the server's `apiJson` exactly.
 *
 * Every setting here decides what happens when the two ends are briefly *not* the same
 * build — during a rolling deploy, or on a phone a month behind:
 *
 * - Unknown keys are ignored, so an older client survives a newer server.
 * - Defaults are written out, because a field omitted from the wire is filled in from the
 *   reader's default, and the value would silently change in transit if the two builds
 *   disagreed about what that default was.
 * - Nulls stay explicit, because null is a value here: a null `deletedAt` is a live row,
 *   and a tombstone that vanishes is a row that comes back from the dead.
 *
 * A caveat worth recording, because it was checked rather than assumed: flipping
 * `encodeDefaults` off here breaks no test. Now that both ends compile against the *same*
 * envelope classes, their defaults are identical by construction, so a field omitted on the
 * way out is refilled with the same value on the way in.
 *
 * That does not make the setting pointless — it is insurance against the two ends being
 * different builds, where the defaults genuinely could differ — but no test here simulates
 * that, so this is deliberate belt-and-braces rather than a property anything proves.
 * `SharedModelContractTest` covers the server's half; `SyncOverHttpTest` covers the two
 * interoperating as they are built today.
 */
val syncJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

/**
 * Builds the client the transport uses.
 *
 * Takes an engine rather than choosing one, because there is no engine that runs on all
 * four targets — each platform supplies its own, and tests supply a fake one so the
 * transport can be exercised without a socket.
 */
fun syncHttpClient(configure: HttpClientConfig<*>.() -> Unit = {}): HttpClient =
    HttpClient {
        install(ContentNegotiation) {
            json(syncJson)
        }
        configure()
    }
