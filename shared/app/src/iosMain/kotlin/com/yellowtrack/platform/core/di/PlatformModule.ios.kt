package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.core.common.storage.IosVolumeInspector
import com.yellowtrack.platform.core.common.storage.VolumeInspector
import com.yellowtrack.platform.core.data.auth.KeychainSessionStore
import com.yellowtrack.platform.core.data.auth.SessionStore
import com.yellowtrack.platform.core.data.sync.Connectivity
import com.yellowtrack.platform.core.data.sync.IosConnectivity
import com.yellowtrack.platform.core.database.DatabaseDriverFactory
import com.yellowtrack.platform.core.database.NativeDatabaseDriverFactory
import com.yellowtrack.platform.core.export.DocumentSink
import com.yellowtrack.platform.core.export.IosDocumentSink
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
        single<DocumentSink> { IosDocumentSink() }
        single<VolumeInspector> { IosVolumeInspector() }
        single<SessionStore> { KeychainSessionStore() }
        single<Connectivity> { IosConnectivity() }
    }
