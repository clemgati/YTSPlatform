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

        viewModel { (projectId: ProjectId) ->
            ProjectDetailsViewModel(projectId, get(), get(), get(), get(), get(), get())
        }

        viewModel { (clientId: ClientId) ->
            ClientDetailsViewModel(clientId, get(), get(), get(), get(), get())
        }
    }
