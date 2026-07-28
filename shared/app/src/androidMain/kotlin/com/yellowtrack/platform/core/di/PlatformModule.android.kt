package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.core.database.AndroidDatabaseDriverFactory
import com.yellowtrack.platform.core.database.DatabaseDriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(androidContext()) }
    }
