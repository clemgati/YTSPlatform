package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.common.coroutines.ioDispatcher
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.auth.SessionStudioContext
import com.yellowtrack.platform.core.data.internal.SqlDelightClientRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightCodbRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightContractRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightCrewRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightDeliverableRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightExpenseRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightGearRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightInvoiceRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightLeadRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightLightingRecipeRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightMediaCopyRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightPackingRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightPostProductionRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightProjectRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightQuoteRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightServiceTemplateRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightSessionRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightShotRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightStorageVolumeRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightStudioProfileRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightTalentReleaseRepository
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.data.sync.SyncEngine
import com.yellowtrack.platform.core.data.sync.SyncOutbox
import com.yellowtrack.platform.core.data.sync.Synchroniser
import com.yellowtrack.platform.core.database.DatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * Repository wiring.
 *
 * Each platform module supplies a `DatabaseDriverFactory`; everything above it is shared.
 * The database itself is created lazily on first use — see [DatabaseProvider] — because
 * driver creation suspends and Koin's definitions do not.
 */
val dataModule =
    module {
        single<AppClock> { AppClock.System }
        // AuthRepository lives here rather than with the HTTP client, so that the studio
        // context can be built from it — the repositories need to know which studio they
        // are serving, and that is now an answer only the session has. `AuthApi` is bound
        // by `networkModule`; Koin resolves it lazily, so the direction of the dependency
        // is data <- network as it should be.
        single { AuthRepository(store = get(), api = get()) }
        single<StudioContext> { SessionStudioContext(get()) }

        single { DatabaseProvider(driverFactory = get()) }

        single<ClientRepository> { SqlDelightClientRepository(get(), get(), get(), ioDispatcher, get()) }
        single<ProjectRepository> { SqlDelightProjectRepository(get(), get(), get(), ioDispatcher, get()) }
        single<SessionRepository> { SqlDelightSessionRepository(get(), get(), get(), ioDispatcher, get()) }
        single<ShotRepository> { SqlDelightShotRepository(get(), get(), ioDispatcher) }
        single<CrewRepository> { SqlDelightCrewRepository(get(), get(), ioDispatcher) }
        single<TalentReleaseRepository> { SqlDelightTalentReleaseRepository(get(), get(), ioDispatcher) }
        single<PostProductionRepository> { SqlDelightPostProductionRepository(get(), get(), get(), ioDispatcher) }
        single<DeliverableRepository> { SqlDelightDeliverableRepository(get(), get(), ioDispatcher) }
        single<MediaCopyRepository> { SqlDelightMediaCopyRepository(get(), get(), ioDispatcher) }
        single<GearRepository> { SqlDelightGearRepository(get(), get(), get(), ioDispatcher) }
        single<StudioProfileRepository> { SqlDelightStudioProfileRepository(get(), get(), get(), ioDispatcher, get()) }
        single<StorageVolumeRepository> { SqlDelightStorageVolumeRepository(get(), get(), get(), ioDispatcher) }
        single<PackingRepository> { SqlDelightPackingRepository(get(), get(), ioDispatcher) }
        single<LightingRecipeRepository> { SqlDelightLightingRecipeRepository(get(), get(), get(), ioDispatcher) }
        single<ServiceTemplateRepository> {
            SqlDelightServiceTemplateRepository(
                get(),
                get(),
                get(),
                ioDispatcher,
                get(),
            )
        }

        // Application-lived, so the periodic loop survives navigation. Cancelled only when
        // the process ends, which on every one of these platforms is when the application
        // is gone anyway.
        single { SyncOutbox(get(), get(), ioDispatcher) }

        // ADR 0012: the ledger writes through the server and waits. Its own thing rather than a
        // method on the engine, because writing now and reconciling later are different acts.
        single { RemoteWriter(get()) }

        single {
            val engine = get<SyncEngine>()
            Synchroniser(
                reconcile = engine::sync,
                auth = get(),
                scope = CoroutineScope(SupervisorJob() + ioDispatcher),
                pendingWork = get<SyncOutbox>().pending(),
                connectivity = get(),
                visibility = get(),
            )
        }

        // The engine was written, tested and then left unreachable: nothing constructed it,
        // so no device ever synchronised. `SyncTransport` comes from `networkModule`.
        single {
            SyncEngine(
                provider = get(),
                studioContext = get(),
                transport = get(),
                clients = get(),
                projects = get(),
                sessions = get(),
                clock = get(),
            )
        }

        single<LeadRepository> { SqlDelightLeadRepository(get(), get(), get(), ioDispatcher, get()) }
        single<InvoiceRepository> { SqlDelightInvoiceRepository(get(), get(), get(), ioDispatcher, get()) }
        single<QuoteRepository> { SqlDelightQuoteRepository(get(), get(), get(), ioDispatcher, get()) }
        single<ContractRepository> { SqlDelightContractRepository(get(), get(), get(), ioDispatcher, get()) }
        single<ExpenseRepository> { SqlDelightExpenseRepository(get(), get(), get(), ioDispatcher, get()) }
        single<CodbRepository> { SqlDelightCodbRepository(get(), get(), get(), ioDispatcher, get()) }
    }
