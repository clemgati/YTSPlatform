package com.yellowtrack.platform.app

import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.session.SessionId

/**
 * A place in the application.
 *
 * Replaces the previous approach of holding a `selectedClientId` field on `AppState`,
 * which could only ever express one detail screen — every additional one would have meant
 * another nullable field and another branch.
 */
sealed interface AppRoute {
    /** The top-level section this route belongs to, used to highlight the navigation. */
    val destination: AppDestination

    data object Dashboard : AppRoute {
        override val destination = AppDestination.Dashboard
    }

    data object Clients : AppRoute {
        override val destination = AppDestination.Clients
    }

    data class ClientDetails(
        val clientId: ClientId,
    ) : AppRoute {
        override val destination = AppDestination.Clients
    }

    data class ProjectDetails(
        val projectId: ProjectId,
    ) : AppRoute {
        override val destination = AppDestination.Clients
    }

    data object Sessions : AppRoute {
        override val destination = AppDestination.Sessions
    }

    data class SessionDetails(
        val sessionId: SessionId,
    ) : AppRoute {
        override val destination = AppDestination.Sessions
    }

    data object Ledger : AppRoute {
        override val destination = AppDestination.Ledger
    }

    data object Events : AppRoute {
        override val destination = AppDestination.Events
    }

    data object Studio : AppRoute {
        override val destination = AppDestination.Studio
    }

    data object Settings : AppRoute {
        override val destination = AppDestination.Settings
    }
}

/** The route shown when a top-level destination is selected. */
internal val AppDestination.rootRoute: AppRoute
    get() =
        when (this) {
            AppDestination.Dashboard -> AppRoute.Dashboard
            AppDestination.Clients -> AppRoute.Clients
            AppDestination.Sessions -> AppRoute.Sessions
            AppDestination.Ledger -> AppRoute.Ledger
            AppDestination.Events -> AppRoute.Events
            AppDestination.Studio -> AppRoute.Studio
            AppDestination.Settings -> AppRoute.Settings
        }
