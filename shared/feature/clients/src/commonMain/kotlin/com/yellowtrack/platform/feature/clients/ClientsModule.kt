package com.yellowtrack.platform.feature.clients

import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsViewModel
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsViewModel
import com.yellowtrack.platform.feature.clients.presentation.project.ProjectDetailsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The feature's dependency wiring, composed by the app module.
 *
 * ViewModels are resolved here rather than constructed inside composables — a Route that
 * builds its own repository cannot be swapped for a fake, and cannot share state.
 */
val clientsModule =
    module {
        viewModel { ClientsViewModel(get(), get(), get(), get(), get()) }

        // Named arguments: a dozen positional get() calls resolved by type is a line where
        // a reordering compiles and is wrong.
        viewModel { (projectId: ProjectId) ->
            ProjectDetailsViewModel(
                projectId = projectId,
                projectRepository = get(),
                clientRepository = get(),
                sessionRepository = get(),
                postProductionRepository = get(),
                deliverableRepository = get(),
                contractRepository = get(),
                studioContext = get(),
                studioProfileRepository = get(),
                clock = get(),
            )
        }

        viewModel { (clientId: ClientId) ->
            ClientDetailsViewModel(
                clientId = clientId,
                clientRepository = get(),
                projectRepository = get(),
                sessionRepository = get(),
                studioContext = get(),
                studioProfileRepository = get(),
                clock = get(),
            )
        }
    }
