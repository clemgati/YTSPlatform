package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.core.common.storage.VolumeInspector
import com.yellowtrack.platform.core.common.storage.WebVolumeInspector
import com.yellowtrack.platform.core.data.auth.BrowserSessionStore
import com.yellowtrack.platform.core.data.auth.SessionStore
import com.yellowtrack.platform.core.data.event.IngestPlatform
import com.yellowtrack.platform.core.data.sync.AppVisibility
import com.yellowtrack.platform.core.data.sync.BrowserAppVisibility
import com.yellowtrack.platform.core.data.sync.BrowserConnectivity
import com.yellowtrack.platform.core.data.sync.Connectivity
import com.yellowtrack.platform.core.database.DatabaseDriverFactory
import com.yellowtrack.platform.core.database.WebDatabaseDriverFactory
import com.yellowtrack.platform.core.export.DocumentSink
import com.yellowtrack.platform.core.export.WebDocumentSink
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        // No tethered capture folder here — a phone is not what a camera shoots into.
        single<IngestPlatform> { IngestPlatform.Unavailable }
        // Requires the SQLite worker script to be served by the web application.
        single<DatabaseDriverFactory> { WebDatabaseDriverFactory() }
        single<DocumentSink> { WebDocumentSink() }
        single<VolumeInspector> { WebVolumeInspector() }
        single<SessionStore> { BrowserSessionStore() }
        single<Connectivity> { BrowserConnectivity() }
        single<AppVisibility> { BrowserAppVisibility() }
    }
