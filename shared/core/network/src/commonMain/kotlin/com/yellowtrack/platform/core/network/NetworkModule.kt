package com.yellowtrack.platform.core.network

import com.yellowtrack.platform.core.data.auth.AuthApi
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.document.DocumentSender
import com.yellowtrack.platform.core.data.event.EventsApi
import com.yellowtrack.platform.core.data.event.PhotographUploader
import com.yellowtrack.platform.core.data.sync.SyncTransport
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * Where the server is, supplied rather than assumed.
 *
 * It was a literal pointing at loopback, which is reachable from exactly one machine and no
 * phone. The application passes the value its build was given — see `generateBuildInfo` and
 * `-Pyellowtrack.serverUrl`.
 */
fun networkModule(serverUrl: String) =
    module {
        single { syncHttpClient() }
        single(createdAtStart = false) { serverUrl }

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

        single<EventsApi> {
            val auth = get<AuthRepository>()
            HttpEventsApi(client = get(), baseUrl = get<String>(), credentials = { auth.token() })
        }

        // Stateless: which event and which camera are a runtime choice, so the watcher is
        // handed this rather than the module knowing about either.
        single<PhotographUploader> {
            val auth = get<AuthRepository>()
            HttpPhotographUploader(
                client = get<HttpClient>(),
                baseUrl = get<String>(),
                credentials = { auth.token() },
            )
        }

        single<DocumentSender> {
            val auth = get<AuthRepository>()
            HttpDocumentSender(
                client = get<HttpClient>(),
                baseUrl = get<String>(),
                credentials = { auth.token() },
            )
        }
    }
