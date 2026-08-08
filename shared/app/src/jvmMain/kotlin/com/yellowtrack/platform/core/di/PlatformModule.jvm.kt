package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.core.common.storage.JvmVolumeInspector
import com.yellowtrack.platform.core.common.storage.VolumeInspector
import com.yellowtrack.platform.core.data.auth.FileSessionStore
import com.yellowtrack.platform.core.data.auth.SessionStore
import com.yellowtrack.platform.core.data.sync.AppVisibility
import com.yellowtrack.platform.core.data.sync.Connectivity
import com.yellowtrack.platform.core.data.sync.DesktopConnectivity
import com.yellowtrack.platform.core.database.DatabaseDriverFactory
import com.yellowtrack.platform.core.database.JvmDatabaseDriverFactory
import com.yellowtrack.platform.core.export.DocumentSink
import com.yellowtrack.platform.core.export.JvmDocumentSink
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<DatabaseDriverFactory> { JvmDatabaseDriverFactory() }
        single<DocumentSink> { JvmDocumentSink() }
        single<VolumeInspector> { JvmVolumeInspector() }
        single<SessionStore> { FileSessionStore() }
        single<Connectivity> { DesktopConnectivity() }
        // Deliberately absent rather than unimplemented — see AppVisibility.Unknown. A window
        // regaining focus happens many times an hour and is not "the application was reopened".
        single<AppVisibility> { AppVisibility.Unknown }
    }
