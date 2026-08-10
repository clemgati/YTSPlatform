package com.yellowtrack.platform.feature.events

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.data.event.IngestStatus
import com.yellowtrack.platform.core.data.event.RefusedPhotograph
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.events.presentation.EventRow
import com.yellowtrack.platform.feature.events.presentation.EventsContent
import com.yellowtrack.platform.feature.events.presentation.EventsScreen
import com.yellowtrack.platform.feature.events.presentation.EventsUiState
import com.yellowtrack.platform.feature.events.presentation.OpenEvent
import com.yellowtrack.platform.feature.events.presentation.PersonRow
import com.yellowtrack.platform.feature.events.presentation.SittingRow
import com.yellowtrack.platform.feature.events.presentation.StationRow
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the Events screen so somebody can look at it.
 *
 * Both states are rendered because the second is the one that matters and the one a
 * screenshot of the happy path would never show: an event open, a station running, and a
 * refusal sitting above a list that still works.
 */
class EventsRenderTest {
    @Test
    fun `renders the event list`() {
        render("events-list") {
            EventsUiState(
                content =
                    UiState.Success(
                        EventsContent(
                            events =
                                listOf(
                                    EventRow(
                                        "event-1",
                                        "Harbour Awards 2026",
                                        null,
                                        openStations = 2,
                                        photographs = 412,
                                    ),
                                    EventRow("event-2", "Saturday walk-ups", null, openStations = 0, photographs = 88),
                                ),
                        ),
                    ),
            )
        }
    }

    /**
     * The first screen a new studio ever sees.
     *
     * Never rendered until a real one was opened and found to be a dead end: a message, and
     * no way to create anything. Rendered now precisely because it is the state with nothing
     * in it — the one easiest to skip when choosing what to look at, and the one every studio
     * meets first.
     */
    @Test
    fun `renders the empty event list`() {
        render("events-empty") {
            EventsUiState(content = UiState.Success(EventsContent(events = emptyList())))
        }
    }

    /**
     * The state worth looking at.
     *
     * A photographer has tried to open a second station on a camera that already has one. The
     * refusal names the camera, and the station they would have to close is still on screen
     * underneath it — which is the whole argument for showing a problem over the screen rather
     * than instead of it, and is only checkable by looking.
     */
    @Test
    fun `renders an open event with a refusal over it`() {
        render("events-open") {
            EventsUiState(
                content =
                    UiState.Success(
                        EventsContent(
                            events = listOf(EventRow("event-1", "Harbour Awards 2026", null, 2, 412)),
                            open =
                                OpenEvent(
                                    id = "event-1",
                                    name = "Harbour Awards 2026",
                                    stations =
                                        listOf(
                                            StationRow("s1", "Bay 1", "Camera A", openedAt = 1_000, closedAt = null),
                                            StationRow("s2", "Bay 2", "Camera B", openedAt = 900, closedAt = 950),
                                        ),
                                    registrations =
                                        listOf(
                                            PersonRow("r1", "ada@example.test", "Ada Okafor"),
                                            PersonRow("r2", "grace@example.test", "Grace Hopper"),
                                        ),
                                    sittings =
                                        listOf(
                                            SittingRow(
                                                id = "sit-1",
                                                registrationId = "r1",
                                                email = "ada@example.test",
                                                name = "Ada Okafor",
                                                stationName = "Bay 1",
                                                closedAt = null,
                                                deliveredAt = null,
                                                photographs = 6,
                                            ),
                                            SittingRow(
                                                id = "sit-2",
                                                registrationId = "r2",
                                                email = "grace@example.test",
                                                name = "Grace Hopper",
                                                stationName = "Bay 2",
                                                closedAt = 950,
                                                deliveredAt = null,
                                                photographs = 4,
                                            ),
                                            SittingRow(
                                                id = "sit-3",
                                                registrationId = "r3",
                                                email = "katherine@example.test",
                                                name = "Katherine Johnson",
                                                stationName = "Bay 2",
                                                closedAt = 900,
                                                deliveredAt = 940,
                                                photographs = 3,
                                            ),
                                        ),
                                ),
                        ),
                    ),
                problem = "A station is already open on Camera A. Close it before opening another.",
                canWatchFolders = true,
                ingest =
                    mapOf(
                        "Camera A" to
                            IngestStatus(
                                sourceKey = "Camera A",
                                folderName = "Camera A",
                                sent = 214,
                                waiting = 2,
                                refused = listOf(RefusedPhotograph("DSC_0188.JPG", "that photograph was empty")),
                            ),
                    ),
            )
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        name: String,
        state: () -> EventsUiState,
    ) {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, "$name.png")

        val scene =
            ImageComposeScene(width = 1_280, height = 2_000, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = YTTheme.colors.background) {
                        EventsScreen(
                            uiState = state(),
                            onRetry = {},
                            onOpenEvent = {},
                            onCloseEvent = {},
                            onCreateEvent = {},
                            onOpenStation = { _, _, _ -> },
                            onCloseStation = { _, _ -> },
                            onWatchFolder = { _, _ -> },
                            onStopWatching = {},
                            onSeat = { _, _, _ -> },
                            onDeliver = { _, _ -> },
                            onDismissNote = {},
                            onDismissProblem = {},
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
