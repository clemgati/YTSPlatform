package com.yellowtrack.platform.feature.clients

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.project.Project
import com.yellowtrack.platform.core.model.project.ProjectId
import com.yellowtrack.platform.core.model.project.ProjectStatus
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.testing.TestData
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.component.ClientFormDialog
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsScreen
import com.yellowtrack.platform.feature.clients.presentation.details.component.ClientQuickActionsSection
import com.yellowtrack.platform.feature.clients.presentation.details.model.ClientRemoval
import com.yellowtrack.platform.feature.clients.presentation.details.preview.ClientDetailsPreviewData
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsScreen
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsUiState
import com.yellowtrack.platform.feature.clients.presentation.project.ProjectDetailsScreen
import com.yellowtrack.platform.feature.clients.presentation.project.ProjectDetailsUiState
import com.yellowtrack.platform.feature.clients.presentation.project.mapper.toDetailsModel
import com.yellowtrack.platform.feature.clients.presentation.project.model.ProjectRemoval
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Renders the Clients screen and its form off-screen so they can be looked at.
 *
 * The empty state matters most here: it has invited the studio to "add your first client"
 * since 0.3.0 while offering no way to do it, and an invitation with no affordance behind
 * it is the kind of thing only looking at the screen catches.
 *
 * Colours in the dialog image read darker than the running app — off-screen, the scrim
 * composites over the dialog as well as behind it. Layout and wording are faithful.
 */
class ClientsScreenRenderTest {
    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        name: String,
        height: Int,
        content: @Composable () -> Unit,
    ) {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, name)

        val scene =
            ImageComposeScene(width = WIDTH, height = height, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = YTTheme.colors.background,
                    ) {
                        content()
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

    @Test
    fun `renders the empty state with a way out of it`() {
        render("clients-empty.png", height = 900) {
            ClientsScreen(
                uiState = ClientsUiState(clients = UiState.Empty),
                onRetry = {},
                onQueryChange = {},
                onClientSelected = {},
                onAddClient = {},
            )
        }
    }

    @Test
    fun `renders the client detail page`() {
        render("client-details.png", height = 2_400) {
            ClientDetailsScreen(
                uiState = ClientDetailsPreviewData.successState,
                onRetry = {},
                onBack = {},
                onScheduleSession = {},
                onAddProject = {},
                onOpenBooking = {},
                onUpdateClient = {},
                onRemoveClient = {},
            )
        }
    }

    /**
     * Both states of the removal control, on their own so the difference is the whole
     * picture: live for an account nothing is booked against, and held — with the reason
     * written underneath — for one that carries bookings.
     */
    @Test
    fun `renders the quick actions for a client that can be removed`() {
        render("client-actions-removable.png", height = 700) {
            ClientQuickActionsSection(
                onAddProject = {},
                onScheduleSession = {},
                onEditClient = {},
                removal = ClientRemoval.Available,
                onRemoveClient = {},
            )
        }
    }

    /**
     * The booking page, which has never been looked at — it has no preview and no render,
     * so whatever it says has only ever been read in source. Rendered in both removal
     * states, since that is what has just changed on it.
     */
    @Test
    fun `renders the booking page for a booking that can be removed`() {
        render("booking-details-removable.png", height = 2_300) {
            ProjectDetailsScreen(
                uiState = bookingState(ProjectRemoval.Available),
                onRetry = {},
                onBack = {},
                onSessionSelected = {},
                onAddTask = {},
                onCompleteTask = { _, _ -> },
                onReopenTask = {},
                onDeleteTask = {},
                onUpdateProject = {},
                onAddDeliverable = {},
                onSetDeliverableStatus = { _, _ -> },
                onAddRevision = {},
                onRemoveDeliverable = {},
                onRemoveProject = {},
            )
        }
    }

    @Test
    fun `renders the booking page for a booking held by what is on it`() {
        render("booking-details-held.png", height = 2_300) {
            ProjectDetailsScreen(
                uiState =
                    bookingState(
                        ProjectRemoval.HeldBy(
                            listOf(
                                ProjectRemoval.Hold(ProjectRemoval.Kind.Invoice, 2),
                                ProjectRemoval.Hold(ProjectRemoval.Kind.ShootDay, 1),
                            ),
                        ),
                    ),
                onRetry = {},
                onBack = {},
                onSessionSelected = {},
                onAddTask = {},
                onCompleteTask = { _, _ -> },
                onReopenTask = {},
                onDeleteTask = {},
                onUpdateProject = {},
                onAddDeliverable = {},
                onSetDeliverableStatus = { _, _ -> },
                onAddRevision = {},
                onRemoveDeliverable = {},
                onRemoveProject = {},
            )
        }
    }

    private fun bookingState(removal: ProjectRemoval): ProjectDetailsUiState {
        val now = Instant.fromEpochMilliseconds(1_781_000_000_000)
        val client = TestData.couple()
        val project =
            Project(
                id = ProjectId("project-1"),
                studioId = client.studioId,
                clientId = client.id,
                name = "Okafor — Wedding",
                serviceLine = ServiceLine.Wedding,
                status = ProjectStatus.Booked,
                contractValue = Money(minorUnits = 450_000, currency = CurrencyCode.GBP),
                enquiredAt = now,
                bookedAt = now,
                audit = AuditMetadata.createdAt(now),
            )

        return ProjectDetailsUiState(
            project =
                UiState.Success(
                    project.toDetailsModel(
                        client = client,
                        sessions = emptyList(),
                        tasks = emptyList(),
                        deliverables = emptyList(),
                        contract = null,
                        now = now,
                        removal = removal,
                    ),
                ),
            currency = CurrencyCode.GBP,
        )
    }

    @Test
    fun `renders the quick actions for a client held by bookings`() {
        render("client-actions-held.png", height = 700) {
            ClientQuickActionsSection(
                onAddProject = {},
                onScheduleSession = {},
                onEditClient = {},
                removal = ClientRemoval.HeldByBookings(count = 3),
                onRemoveClient = {},
            )
        }
    }

    @Test
    fun `renders the client form`() {
        render("client-form.png", height = 1_600) {
            ClientFormDialog(onSave = {}, onDismiss = {})
        }
    }

    private companion object {
        const val WIDTH = 1_280
    }
}
