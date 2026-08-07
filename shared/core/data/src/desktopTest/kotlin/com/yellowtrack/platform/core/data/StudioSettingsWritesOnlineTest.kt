package com.yellowtrack.platform.core.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.SqlDelightServiceTemplateRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightStudioProfileRepository
import com.yellowtrack.platform.core.data.internal.SyncTables
import com.yellowtrack.platform.core.data.sync.RemoteWriter
import com.yellowtrack.platform.core.data.sync.SyncTransport
import com.yellowtrack.platform.core.data.sync.WriteFailed
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.model.sync.SyncPullResponse
import com.yellowtrack.platform.core.model.sync.SyncPushOutcome
import com.yellowtrack.platform.core.model.sync.SyncPushRequest
import com.yellowtrack.platform.core.model.sync.SyncPushResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The two entities that generated every conflict this application ever recorded, moved to
 * writing through the server.
 *
 * Worth stating plainly, because it is the reason this file exists rather than a general
 * tidying: in production `service_template` and `studio_profile` accounted for **all** of
 * them — 128 and 38 of the original 166, then 8 and 2 of the ten that came back. They are
 * desk-bound, so ADR 0012 decision 3 does not protect them, and nothing is lost by requiring
 * a connection to change a price list.
 *
 * These replace the outbox coverage the two used to be held to. Removing them from that list
 * without putting anything in its place would have left them the only repositories in the
 * package asserting nothing about where their writes go.
 */
class StudioSettingsWritesOnlineTest {
    @Test
    fun `a template reaches the server before it reaches the cache`() =
        runTest {
            val transport = RecordingTransport()
            val provider = testDatabaseProvider()
            val templates = templates(provider, transport)

            templates.saveTemplate(template())

            assertEquals(
                "Full-day wedding",
                transport.pushed
                    .single()
                    .serviceTemplates
                    .single()
                    .name,
                "the server should have been asked",
            )
            assertNotNull(templates.getTemplate(ServiceTemplateId(TEMPLATE)), "and the cache should hold it")
        }

    @Test
    fun `a template that could not be sent is not left in the cache`() =
        runTest {
            val provider = testDatabaseProvider()
            val templates = templates(provider, RecordingTransport(failing = true))

            assertFailsWith<WriteFailed.Offline> { templates.saveTemplate(template()) }

            assertNull(
                templates.getTemplate(ServiceTemplateId(TEMPLATE)),
                "a package that did not reach the server must not look like one that did",
            )
        }

    /**
     * The delete this repository forgot to queue for three releases, which survived only
     * because no screen could reach it. There is no separate step to forget any more — the
     * write is the send.
     */
    @Test
    fun `retiring a template travels as a tombstone and only then leaves the cache`() =
        runTest {
            val transport = RecordingTransport()
            val provider = testDatabaseProvider()
            val templates = templates(provider, transport)
            templates.saveTemplate(template())
            transport.pushed.clear()

            templates.deleteTemplate(ServiceTemplateId(TEMPLATE))

            val sent =
                transport.pushed
                    .single()
                    .serviceTemplates
                    .single()
            assertTrue(sent.audit.isDeleted, "a delete is the row carrying a tombstone, not an instruction")
            assertNull(templates.getTemplate(ServiceTemplateId(TEMPLATE)))
        }

    @Test
    fun `a retirement that could not be sent leaves the template where it was`() =
        runTest {
            val transport = RecordingTransport()
            val provider = testDatabaseProvider()
            val templates = templates(provider, transport)
            templates.saveTemplate(template())
            transport.failing = true

            assertFailsWith<WriteFailed.Offline> { templates.deleteTemplate(ServiceTemplateId(TEMPLATE)) }

            assertNotNull(
                templates.getTemplate(ServiceTemplateId(TEMPLATE)),
                "the package a studio asked to retire is still there, which is the truth",
            )
        }

    @Test
    fun `the profile reaches the server before it reaches the cache`() =
        runTest {
            val transport = RecordingTransport()
            val provider = testDatabaseProvider()
            val profiles = profiles(provider, transport)

            profiles.saveProfile(profile())

            assertEquals(
                "Clement's Photos",
                transport.pushed
                    .single()
                    .studioProfiles
                    .single()
                    .name,
            )
            assertNotNull(profiles.getProfile())
        }

    /**
     * The one that was actually happening. A device with an empty cache used to write the
     * profile locally and queue it; the queue sent it at whatever version the device had
     * invented, and the server recorded a conflict against its own.
     */
    @Test
    fun `a profile that could not be sent is not left in the cache`() =
        runTest {
            val provider = testDatabaseProvider()
            val profiles = profiles(provider, RecordingTransport(failing = true))

            assertFailsWith<WriteFailed.Offline> { profiles.saveProfile(profile()) }

            assertNull(profiles.getProfile(), "nothing to push later means nothing to conflict with")
        }

    @Test
    fun `neither of them queues anything for later`() =
        runTest {
            val provider = testDatabaseProvider()
            val transport = RecordingTransport()

            templates(provider, transport).saveTemplate(template())
            profiles(provider, transport).saveProfile(profile())

            val queued =
                provider
                    .database()
                    .outboxQueries
                    .selectPendingIdentities(STUDIO.value)
                    .awaitAsList()
                    .map { it.entity_table }

            assertEquals(
                emptyList(),
                queued.filter { it == SyncTables.SERVICE_TEMPLATE || it == SyncTables.STUDIO_PROFILE },
                "no outbox means no second write path, which is where the conflicts came from",
            )
        }

    // -- Fixtures ------------------------------------------------------------------------------

    private fun templates(
        provider: com.yellowtrack.platform.core.database.DatabaseProvider,
        transport: SyncTransport,
    ) = SqlDelightServiceTemplateRepository(
        provider,
        LocalStudioContext(),
        AppClock { NOW },
        Dispatchers.Unconfined,
        RemoteWriter(transport),
    )

    private fun profiles(
        provider: com.yellowtrack.platform.core.database.DatabaseProvider,
        transport: SyncTransport,
    ) = SqlDelightStudioProfileRepository(
        provider,
        LocalStudioContext(),
        AppClock { NOW },
        Dispatchers.Unconfined,
        RemoteWriter(transport),
    )

    private fun template() =
        ServiceTemplate(
            id = ServiceTemplateId(TEMPLATE),
            studioId = STUDIO,
            name = "Full-day wedding",
            serviceLine = ServiceLine.Wedding,
            defaultSessionDurationMinutes = 600,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun profile() =
        StudioProfile
            .empty(STUDIO, AuditMetadata.createdAt(NOW))
            .copy(name = "Clement's Photos", currency = CurrencyCode.USD)

    private class RecordingTransport(
        var failing: Boolean = false,
    ) : SyncTransport {
        val pushed = mutableListOf<SyncPushRequest>()

        override suspend fun pull(
            since: Long,
            limit: Int,
        ) = SyncPullResponse(cursor = since, hasMore = false)

        override suspend fun push(changes: SyncPushRequest): List<SyncPushResult> {
            if (failing) throw IllegalStateException("no connection")
            pushed += changes
            return changes.identitiesForTest().map {
                SyncPushResult(it.first, it.second, SyncPushOutcome.Applied, 1)
            }
        }
    }

    private companion object {
        val STUDIO = LocalStudioContext.LOCAL_STUDIO_ID
        val NOW: Instant = Instant.fromEpochMilliseconds(1_786_090_014_135)
        const val TEMPLATE = "template-1"
    }
}

private fun SyncPushRequest.identitiesForTest(): List<Pair<String, String>> =
    serviceTemplates.map { SyncTables.SERVICE_TEMPLATE to it.id.value } +
        studioProfiles.map { SyncTables.STUDIO_PROFILE to it.id.value }
