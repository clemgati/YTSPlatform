package com.yellowtrack.platform.core.di

import com.yellowtrack.platform.app.BuildInfo
import com.yellowtrack.platform.core.data.dataModule
import com.yellowtrack.platform.core.network.networkModule
import com.yellowtrack.platform.feature.auth.authFeatureModule
import com.yellowtrack.platform.feature.clients.clientsModule
import com.yellowtrack.platform.feature.dashboard.dashboardModule
import com.yellowtrack.platform.feature.ledger.ledgerModule
import com.yellowtrack.platform.feature.sessions.sessionsModule
import com.yellowtrack.platform.feature.settings.settingsModule
import com.yellowtrack.platform.feature.studio.studioModule
import org.koin.dsl.module

/**
 * Dependencies shared by every platform.
 *
 * Only the app module composes features; features never reference one another.
 */
val appModule =
    module {
        includes(
            dataModule,
            networkModule(BuildInfo.SERVER_URL),
            authFeatureModule,
            dashboardModule,
            clientsModule,
            ledgerModule,
            sessionsModule,
            settingsModule,
            studioModule,
        )
    }
