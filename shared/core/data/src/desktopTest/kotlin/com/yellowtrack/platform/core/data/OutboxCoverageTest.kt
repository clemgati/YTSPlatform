package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.common.time.AppClock
import com.yellowtrack.platform.core.data.internal.OutboxOperation
import com.yellowtrack.platform.core.data.internal.SqlDelightGearRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightLightingRecipeRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightMediaCopyRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightPackingRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightPostProductionRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightServiceTemplateRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightShotRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightStorageVolumeRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightStudioProfileRepository
import com.yellowtrack.platform.core.data.internal.SqlDelightTalentReleaseRepository
import com.yellowtrack.platform.core.data.internal.SyncTables
import com.yellowtrack.platform.core.data.sync.applyClient
import com.yellowtrack.platform.core.data.sync.applyProject
import com.yellowtrack.platform.core.data.sync.applySession
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.post.PostProductionTask
import com.yellowtrack.platform.core.model.post.PostProductionTaskId
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplate
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import com.yellowtrack.platform.core.model.studio.StudioProfile
import com.yellowtrack.platform.core.model.studio.StudioProfileId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Saving through a repository puts the row in the outbox.
 *
 * This is the layer the other two guards cannot see. `SyncFieldCoverageTest` proves a row
 * survives the database; the upload guard in `SyncEngineTest` proves the engine reads every
 * table it can be asked for — but that test *queues the rows itself*, so it says nothing
 * about whether anything ever queues one in the ordinary course of using the application.
 *
 * Four entities shipped through that blind spot. Gear items, packing entries, storage
 * volumes and media copies were wired into the envelope, the routes, the apply loop and the
 * push read, and their repositories never enqueued. A device pulled them from other devices
 * and never sent its own, and every test passed.
 */
class OutboxCoverageTest {
    @Test
    fun `every repository queues what it saves`() =
        runTest {
            val provider = testDatabaseProvider()
            val database = provider.database()
            val studio = LocalStudioContext()
            val clock = AppClock { NOW }

            // The rows these hang off, written straight to the tables so the outbox holds
            // only what the repositories put there.
            database.applyClient(client())
            database.applyProject(project())
            database.applySession(session())

            SqlDelightGearRepository(provider, studio, clock, Dispatchers.Unconfined)
                .saveGearItem(gearItem())
            SqlDelightPackingRepository(provider, clock, Dispatchers.Unconfined)
                .savePackingEntry(packingEntry())
            SqlDelightLightingRecipeRepository(provider, studio, clock, Dispatchers.Unconfined)
                .saveRecipe(recipe())
            SqlDelightStorageVolumeRepository(provider, studio, clock, Dispatchers.Unconfined)
                .saveVolume(volume())
            SqlDelightMediaCopyRepository(provider, clock, Dispatchers.Unconfined)
                .saveCopy(mediaCopy())
            SqlDelightShotRepository(provider, clock, Dispatchers.Unconfined)
                .saveShot(shot())
            SqlDelightTalentReleaseRepository(provider, clock, Dispatchers.Unconfined)
                .saveRelease(release())
            SqlDelightPostProductionRepository(provider, studio, clock, Dispatchers.Unconfined)
                .saveTask(task())
            SqlDelightStudioProfileRepository(provider, studio, clock, Dispatchers.Unconfined)
                .saveProfile(studioProfile())

            val queued =
                database.outboxQueries
                    .selectPending(studio.studioId.value, 200)
                    .executeAsList()
                    .map { it.entity_table }
                    .toSet()

            val expected =
                setOf(
                    SyncTables.GEAR_ITEM,
                    SyncTables.PACKING_ENTRY,
                    SyncTables.LIGHTING_RECIPE,
                    SyncTables.STORAGE_VOLUME,
                    SyncTables.MEDIA_COPY,
                    SyncTables.SHOT,
                    SyncTables.TALENT_RELEASE,
                    SyncTables.POST_TASK,
                    SyncTables.STUDIO_PROFILE,
                )

            assertEquals(
                emptySet(),
                expected - queued,
                "these repositories saved a row and queued nothing. The work stays on the device " +
                    "it was entered on, and every other test still passes",
            )
        }

    /**
     * And queues what it removes.
     *
     * The sibling blind spot, and it had one repository sitting in it: `deleteTemplate`
     * retired the row and queued nothing, so a package removed on one device stayed on
     * every other one for good. It survived because no screen could remove a template —
     * the path was never walked until service templates became editable.
     *
     * A tombstone that is never sent is worse than a delete that fails outright. The studio
     * sees the row go, believes it, and the row is still on the laptop.
     */
    @Test
    fun `every repository queues what it removes`() =
        runTest {
            val provider = testDatabaseProvider()
            val database = provider.database()
            val studio = LocalStudioContext()
            val clock = AppClock { NOW }

            val templates = SqlDelightServiceTemplateRepository(provider, studio, clock, Dispatchers.Unconfined)
            templates.saveTemplate(serviceTemplate())
            templates.deleteTemplate(ServiceTemplateId(TEMPLATE))

            val gear = SqlDelightGearRepository(provider, studio, clock, Dispatchers.Unconfined)
            gear.saveGearItem(gearItem())
            gear.deleteGearItem(GearItemId(GEAR))

            val queued =
                database.outboxQueries
                    .selectPending(studio.studioId.value, 200)
                    .executeAsList()
                    .filter { it.operation == OutboxOperation.Delete.name }
                    .map { it.entity_table }
                    .toSet()

            assertEquals(
                emptySet(),
                setOf(SyncTables.SERVICE_TEMPLATE, SyncTables.GEAR_ITEM) - queued,
                "these repositories retired a row and told nobody. The row disappears here " +
                    "and stays on every other device, which is the fault 0.7.0 fixed and this " +
                    "one repository kept",
            )
        }

    // -- Fixtures ----------------------------------------------------------------------------

    private fun serviceTemplate() =
        ServiceTemplate(
            id = ServiceTemplateId(TEMPLATE),
            studioId = STUDIO,
            name = "Full-day wedding",
            serviceLine = ServiceLine.Wedding,
            defaultSessionDurationMinutes = 600,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun client() =
        Client(
            id = ClientId(CLIENT),
            studioId = STUDIO,
            accountName = "Okafor",
            accountType = ClientAccountType.Individual,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun project() =
        Project(
            id = ProjectId(PROJECT),
            studioId = STUDIO,
            clientId = ClientId(CLIENT),
            name = "Okafor — Wedding",
            serviceLine = ServiceLine.Wedding,
            status = ProjectStatus.Booked,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun session() =
        Session(
            id = SessionId(SESSION),
            studioId = STUDIO,
            projectId = ProjectId(PROJECT),
            title = "Ceremony",
            kind = SessionKind.Shoot,
            status = SessionStatus.Scheduled,
            startsAt = NOW,
            endsAt = NOW,
            timeZoneId = "Europe/London",
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun gearItem() =
        GearItem(id = GearItemId(GEAR), studioId = STUDIO, name = "35mm", audit = AuditMetadata.createdAt(NOW))

    private fun packingEntry() =
        PackingEntry(
            id = PackingEntryId("pk-1"),
            studioId = STUDIO,
            sessionId = SessionId(SESSION),
            gearItemId = GearItemId(GEAR),
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun recipe() =
        LightingRecipe(
            id = LightingRecipeId("lr-1"),
            studioId = STUDIO,
            name = "Clamshell",
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun volume() =
        StorageVolume(
            id = StorageVolumeId(VOLUME),
            studioId = STUDIO,
            label = "Shuttle 1",
            kind = StorageKind.Computer,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun mediaCopy() =
        MediaCopy(
            id = MediaCopyId("mc-1"),
            studioId = STUDIO,
            sessionId = SessionId(SESSION),
            volumeId = StorageVolumeId(VOLUME),
            volumeName = "Shuttle 1",
            kind = StorageKind.Computer,
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun shot() =
        Shot(
            id = ShotId("sh-1"),
            studioId = STUDIO,
            sessionId = SessionId(SESSION),
            description = "Rings",
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun release() =
        TalentRelease(
            id = TalentReleaseId("tr-1"),
            studioId = STUDIO,
            sessionId = SessionId(SESSION),
            personName = "Rosa Iyer",
            audit = AuditMetadata.createdAt(NOW),
        )

    /** Keyed by the studio, which is what makes one row of it rather than two. */
    private fun studioProfile() =
        StudioProfile(
            id = StudioProfileId(STUDIO.value),
            studioId = STUDIO,
            name = "Harbourline Photography",
            audit = AuditMetadata.createdAt(NOW),
        )

    private fun task() =
        PostProductionTask(
            id = PostProductionTaskId("pt-1"),
            studioId = STUDIO,
            projectId = ProjectId(PROJECT),
            name = "Cull",
            audit = AuditMetadata.createdAt(NOW),
        )

    private companion object {
        val STUDIO = TEST_STUDIO_ID
        val NOW: Instant = TEST_NOW
        const val CLIENT = "client-1"
        const val PROJECT = "project-1"
        const val SESSION = "session-1"
        const val GEAR = "gear-1"
        const val TEMPLATE = "template-1"
        const val VOLUME = "volume-1"
    }
}
