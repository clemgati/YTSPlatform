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
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.session.Session
import com.yellowtrack.platform.core.model.session.SessionId
import com.yellowtrack.platform.core.model.session.SessionKind
import com.yellowtrack.platform.core.model.session.SessionStatus
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsScreen
import com.yellowtrack.platform.feature.sessions.presentation.details.SessionDetailsUiState
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

        val scene =
            ImageComposeScene(width = 1_280, height = 2_600, density = Density(2f)) {
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
                                            session.toDetailsModel(project, client, zone),
                                        ),
                                    today = LocalDate(2026, 7, 28),
                                ),
                            onRetry = {},
                            onBack = {},
                            onUpdateSession = {},
                            onMoveSession = {},
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
}
