package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.core.common.storage.JvmVolumeInspector
import com.yellowtrack.platform.core.common.storage.VolumeInspector
import com.yellowtrack.platform.core.database.AndroidDatabaseDriverFactory
import com.yellowtrack.platform.core.database.DatabaseDriverFactory
import com.yellowtrack.platform.core.export.AndroidDocumentSink
import com.yellowtrack.platform.core.export.DocumentSink
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
        single<DocumentSink> { AndroidDocumentSink(androidContext()) }
        single<VolumeInspector> { JvmVolumeInspector() }
    }
