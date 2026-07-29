package com.yellowtrack.platform.feature.sessions

import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.feature.sessions.presentation.SessionsViewModel
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sessionsModule =
    module {
        viewModel { SessionsViewModel(get(), get(), get(), get(), get()) }

        viewModel { (sessionId: SessionId) ->
            SessionDetailsViewModel(
                sessionId = sessionId,
                sessionRepository = get(),
                shotRepository = get(),
                crewRepository = get(),
                releaseRepository = get(),
                mediaCopyRepository = get(),
                packingRepository = get(),
                gearRepository = get(),
                projectRepository = get(),
                clientRepository = get(),
                documentSink = get(),
                studioContext = get(),
                clock = get(),
            )
        }
    }
