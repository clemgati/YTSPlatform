package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.core.database.DatabaseDriverFactory
import com.yellowtrack.platform.core.database.NativeDatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
    }
