package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.core.common.storage.VolumeInspector
import com.yellowtrack.platform.core.common.storage.WebVolumeInspector
import com.yellowtrack.platform.core.database.DatabaseDriverFactory
import com.yellowtrack.platform.core.database.WebDatabaseDriverFactory
import com.yellowtrack.platform.core.export.DocumentSink
import com.yellowtrack.platform.core.export.WebDocumentSink
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        // Requires the SQLite worker script to be served by the web application.
        single<DatabaseDriverFactory> { WebDatabaseDriverFactory() }
        single<DocumentSink> { WebDocumentSink() }
        single<VolumeInspector> { WebVolumeInspector() }
    }
