package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.auth.AuthApi
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.sync.SyncTransport
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Where the server is.
 *
 * A loopback default, because that is what `./gradlew :server:run` produces and there is no
 * deployment yet. It is a single binding so that pointing a build at a real host is one
 * override rather than a search for string literals.
 */
const val DEFAULT_SERVER_URL: String = "http://localhost:8080"

val networkModule =
    module {
        single { syncHttpClient() }
        single(createdAtStart = false) { DEFAULT_SERVER_URL }

        single<AuthApi> { HttpAuthApi(client = get(), baseUrl = get<String>()) }

        // The transport reads the token per request rather than holding one, so signing out
        // takes effect on the next call instead of leaving a live client behind.
        single<SyncTransport> {
            val auth = get<AuthRepository>()
            HttpSyncTransport(
                client = get<HttpClient>(),
                baseUrl = get<String>(),
                credentials = { auth.token() },
            )
        }
    }
