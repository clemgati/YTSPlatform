package com.yellowtrack.platform.core.di

import org.koin.dsl.module

/**
 * Dependencies whose implementations are shared by every platform.
 *
 * YTP-009A intentionally leaves this module empty. Services,
 * repositories, and use cases will be registered in later tasks.
 */
val appModule =
    module {
        // Shared dependencies will be registered here.
    }
