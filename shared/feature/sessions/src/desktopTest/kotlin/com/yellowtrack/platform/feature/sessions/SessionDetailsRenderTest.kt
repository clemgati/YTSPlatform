package com.yellowtrack.platform.feature.sessions

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.common.solar.GeoCoordinates
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.model.client.Client
import com.yellowtrack.platform.core.model.client.ClientAccountType
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.crew.CrewMember
import com.yellowtrack.platform.core.model.crew.CrewMemberId
import com.yellowtrack.platform.core.model.crew.CrewRole
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.media.MediaCopy
import com.yellowtrack.platform.core.model.media.MediaCopyId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.release.ReleaseKind
import com.yellowtrack.platform.core.model.release.ReleaseStatus
import com.yellowtrack.platform.core.model.release.TalentRelease
import com.yellowtrack.platform.core.model.release.TalentReleaseId
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.model.shot.Shot
import com.yellowtrack.platform.core.model.shot.ShotId
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsScreen
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsUiState
import com.yellowtrack.platform.feature.sessions.presentation.details.mapper.sessionRemoval
import com.yellowtrack.platform.feature.sessions.presentation.details.mapper.toDetailsModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the session detail page, so the light panel can be looked at. */
class SessionDetailsRenderTest {
    private val zone = TimeZone.of("Europe/London")

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `renders a shoot day with its light`() {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, "session-details.png")

        val clientId = ClientId.new()
        val projectId = ProjectId.new()

        val client =
            Client(
                id = clientId,
                studioId = LocalStudioContext.LOCAL_STUDIO_ID,
                accountName = "Sarah & Michael Johnson",
                accountType = ClientAccountType.Couple,
                audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
            )

        val project =
            Project(
                id = projectId,
                studioId = LocalStudioContext.LOCAL_STUDIO_ID,
                clientId = clientId,
                name = "Johnson Wedding",
                serviceLine = ServiceLine.Wedding,
                status = ProjectStatus.Booked,
                audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
            )

        val session =
            Session(
                id = SessionId.new(),
                studioId = LocalStudioContext.LOCAL_STUDIO_ID,
                projectId = projectId,
                title = "Wedding day",
                kind = SessionKind.Shoot,
                status = SessionStatus.Confirmed,
                startsAt = LocalDateTime.parse("2026-08-15T14:00").toInstant(zone),
                endsAt = LocalDateTime.parse("2026-08-16T01:00").toInstant(zone),
                timeZoneId = zone.id,
                locationName = "Thornbury Manor",
                locationAddress = "Thornbury, Cornwall",
                coordinates = GeoCoordinates(latitude = 50.2, longitude = -5.5),
                callTime = LocalDateTime.parse("2026-08-15T12:30").toInstant(zone),
                notes = "Family formals on the south lawn before the light goes.",
                audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
            )

        val shots =
            listOf(
                shot("Bride with both parents", "Bride's family", "Sarah + Mum and Dad", captured = true),
                shot("Bride with her grandmother", "Bride's family", "Grandma Ruth"),
                shot("Groom with his brothers", "Groom's side", "Michael + Tom + Alex"),
                shot("Detail of the rings", "", null),
            )

        val crew =
            listOf(
                crewMember("Priya Shah", CrewRole.MakeUp, "07700 900123", "09:00"),
                crewMember("Sam Ellis", CrewRole.SecondShooter, "07700 900456", "13:30"),
                crewMember("Alex Reed", CrewRole.Videographer, null, null),
            )

        val releases =
            listOf(
                release("Ada Okafor", ReleaseKind.Adult, ReleaseStatus.Signed, signed = true),
                release("Tom Okafor", ReleaseKind.Minor, ReleaseStatus.Signed, signed = true),
                release("Ben Idris", ReleaseKind.Adult, ReleaseStatus.Pending),
                release("Chloe Marsh", ReleaseKind.Adult, ReleaseStatus.Refused),
            )

        val mediaCopies =
            listOf(
                mediaCopy("Studio iMac", StorageKind.Computer, files = 2_481),
                mediaCopy("Red Samsung T7", StorageKind.ExternalDrive, files = 2_481, path = "/Volumes/Red T7/Johnson"),
                mediaCopy("Backblaze", StorageKind.Cloud, files = null),
            )

        val gear =
            listOf(
                gearItem("Canon R5 body", GearCategory.Camera),
                gearItem("24-70mm f/2.8", GearCategory.Lens),
                gearItem("Profoto B10", GearCategory.Lighting),
                gearItem("Manfrotto stand", GearCategory.Support),
            )

        val packing =
            listOf(
                // Packed and back: the ordinary case.
                packingEntry(session.id, gear[0].id, packed = true, returned = true),
                packingEntry(session.id, gear[1].id, packed = true, returned = true),
                // Packed and still out — the row the section exists to show.
                packingEntry(session.id, gear[2].id, packed = true, returned = false),
                // On the list and never packed.
                packingEntry(session.id, gear[3].id, packed = false, returned = false),
            )

        val scene =
            ImageComposeScene(width = 1_280, height = 7_000, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = YTTheme.colors.background,
                    ) {
                        SessionDetailsScreen(
                            uiState =
                                SessionDetailsUiState(
                                    session =
                                        UiState.Success(
                                            session.toDetailsModel(
                                                project,
                                                client,
                                                shots,
                                                crew,
                                                releases,
                                                mediaCopies,
                                                gear,
                                                packing,
                                                emptyList(),
                                                zone,
                                                removal =
                                                    sessionRemoval(
                                                        backups = mediaCopies.size,
                                                        releases = releases.size,
                                                        crew = crew.size,
                                                        shots = shots.size,
                                                        packedItems = packing.size,
                                                    ),
                                            ),
                                        ),
                                    today = LocalDate(2026, 7, 28),
                                ),
                            onRetry = {},
                            onBack = {},
                            onUpdateSession = {},
                            onMoveSession = {},
                            onAddShot = {},
                            onAddCrew = {},
                            onRemoveCrew = {},
                            onAddRelease = {},
                            onSetReleaseStatus = { _, _ -> },
                            onRemoveRelease = {},
                            onAddMediaCopy = {},
                            onVerifyMediaCopy = {},
                            onCheckMediaCopy = {},
                            onRemoveMediaCopy = {},
                            onToggleShot = { _, _ -> },
                            onDeleteShot = {},
                            onAddPackingGear = {},
                            onSetPacked = { _, _ -> },
                            onSetReturned = { _, _ -> },
                            onRemovePacking = {},
                            onCopyCallSheet = {},
                            onSaveCallSheet = {},
                            onRemoveSession = {},
                            callSheetMessage = null,
                        )
                    }
                }
            }

        try {
            val bytes = requireNotNull(scene.render().encodeToData()) { "Skia produced no image data" }.bytes
            target.writeBytes(bytes)
        } finally {
            scene.close()
        }

        assertTrue(target.length() > 0, "expected a non-empty image at ${target.absolutePath}")
        println("Rendered ${target.absolutePath}")
    }

    private fun shot(
        description: String,
        group: String,
        people: String?,
        captured: Boolean = false,
    ) = Shot(
        id = ShotId.new(),
        studioId = LocalStudioContext.LOCAL_STUDIO_ID,
        sessionId = SessionId.new(),
        description = description,
        group = group.ifBlank { null },
        people = people,
        isCaptured = captured,
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    private fun crewMember(
        name: String,
        role: CrewRole,
        phone: String?,
        callTime: String?,
    ) = CrewMember(
        id = CrewMemberId.new(),
        studioId = LocalStudioContext.LOCAL_STUDIO_ID,
        sessionId = SessionId.new(),
        name = name,
        role = role,
        phone = phone,
        callTime = callTime?.let { LocalDateTime.parse("2026-08-15T$it").toInstant(zone) },
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )

    private fun release(
        personName: String,
        kind: ReleaseKind,
        status: ReleaseStatus,
        signed: Boolean = false,
    ) = TalentRelease(
        id = TalentReleaseId.new(),
        studioId = LocalStudioContext.LOCAL_STUDIO_ID,
        sessionId = SessionId.new(),
        personName = personName,
        kind = kind,
        status = status,
        signedAt = TestAppClock.DEFAULT_NOW.takeIf { signed },
        // Deliberately left unnamed, so the render shows how a void minor's release reads.
        guardianName = null,
        audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
    )
}

private fun gearItem(
    name: String,
    category: GearCategory,
) = GearItem(
    id = GearItemId.new(),
    studioId = LocalStudioContext.LOCAL_STUDIO_ID,
    name = name,
    category = category,
    audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
)

private fun packingEntry(
    sessionId: SessionId,
    gearItemId: GearItemId,
    packed: Boolean,
    returned: Boolean,
) = PackingEntry(
    id = PackingEntryId.new(),
    studioId = LocalStudioContext.LOCAL_STUDIO_ID,
    sessionId = sessionId,
    gearItemId = gearItemId,
    isPacked = packed,
    isReturned = returned,
    audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
)

private fun mediaCopy(
    volumeName: String,
    kind: StorageKind,
    files: Int?,
    path: String? = null,
) = MediaCopy(
    id = MediaCopyId.new(),
    studioId = LocalStudioContext.LOCAL_STUDIO_ID,
    sessionId = SessionId.new(),
    volumeName = volumeName,
    kind = kind,
    path = path,
    copiedAt = TestAppClock.DEFAULT_NOW,
    verifiedAt = TestAppClock.DEFAULT_NOW.takeIf { files != null },
    verifiedFileCount = files,
    verifiedBytes = files?.let { it * 40_000_000L },
    audit = AuditMetadata.createdAt(TestAppClock.DEFAULT_NOW),
)
