package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.core.common.storage.JvmVolumeInspector
import com.yellowtrack.platform.core.common.storage.VolumeInspector
import com.yellowtrack.platform.core.data.auth.AndroidSessionStore
import com.yellowtrack.platform.core.data.auth.SessionStore
import com.yellowtrack.platform.core.data.event.IngestPlatform
import com.yellowtrack.platform.core.data.sync.AndroidAppVisibility
import com.yellowtrack.platform.core.data.sync.AndroidConnectivity
import com.yellowtrack.platform.core.data.sync.AppVisibility
import com.yellowtrack.platform.core.data.sync.Connectivity
import com.yellowtrack.platform.core.database.AndroidDatabaseDriverFactory
import com.yellowtrack.platform.core.database.DatabaseDriverFactory
import com.yellowtrack.platform.core.export.AndroidDocumentSink
import com.yellowtrack.platform.core.export.DocumentSink
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        // No tethered capture folder here — a phone is not what a camera shoots into.
        single<IngestPlatform> { IngestPlatform.Unavailable }
        single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
        single<DocumentSink> { AndroidDocumentSink(androidContext()) }
        single<VolumeInspector> { JvmVolumeInspector() }
        single<SessionStore> { AndroidSessionStore(androidContext()) }
        single<Connectivity> { AndroidConnectivity(androidContext()) }
        single<AppVisibility> { AndroidAppVisibility(androidApplication()) }
    }
