package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.common.coroutines.ioDispatcher
import com.yellowtrack.platform.core.common.time.AppClock
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
import com.yellowtrack.platform.core.data.internal.SqlDelightStudioProfileRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightTalentReleaseRepository
import com.yellowtrack.platform.core.database.DatabaseProvider
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
        single<StudioContext> { LocalStudioContext() }

        single { DatabaseProvider(driverFactory = get()) }

        single<ClientRepository> { SqlDelightClientRepository(get(), get(), get(), ioDispatcher) }
        single<ProjectRepository> { SqlDelightProjectRepository(get(), get(), get(), ioDispatcher) }
        single<SessionRepository> { SqlDelightSessionRepository(get(), get(), get(), ioDispatcher) }
        single<ShotRepository> { SqlDelightShotRepository(get(), get(), ioDispatcher) }
        single<CrewRepository> { SqlDelightCrewRepository(get(), get(), ioDispatcher) }
        single<TalentReleaseRepository> { SqlDelightTalentReleaseRepository(get(), get(), ioDispatcher) }
        single<PostProductionRepository> { SqlDelightPostProductionRepository(get(), get(), get(), ioDispatcher) }
        single<DeliverableRepository> { SqlDelightDeliverableRepository(get(), get(), ioDispatcher) }
        single<MediaCopyRepository> { SqlDelightMediaCopyRepository(get(), get(), ioDispatcher) }
        single<GearRepository> { SqlDelightGearRepository(get(), get(), get(), ioDispatcher) }
        single<StudioProfileRepository> { SqlDelightStudioProfileRepository(get(), get(), get(), ioDispatcher) }
        single<PackingRepository> { SqlDelightPackingRepository(get(), get(), ioDispatcher) }
        single<LightingRecipeRepository> { SqlDelightLightingRecipeRepository(get(), get(), get(), ioDispatcher) }
        single<ServiceTemplateRepository> { SqlDelightServiceTemplateRepository(get(), get(), get(), ioDispatcher) }

        single<LeadRepository> { SqlDelightLeadRepository(get(), get(), get(), ioDispatcher) }
        single<InvoiceRepository> { SqlDelightInvoiceRepository(get(), get(), get(), ioDispatcher) }
        single<QuoteRepository> { SqlDelightQuoteRepository(get(), get(), get(), ioDispatcher) }
        single<ContractRepository> { SqlDelightContractRepository(get(), get(), get(), ioDispatcher) }
        single<ExpenseRepository> { SqlDelightExpenseRepository(get(), get(), get(), ioDispatcher) }
        single<CodbRepository> { SqlDelightCodbRepository(get(), get(), get(), ioDispatcher, get()) }
    }
