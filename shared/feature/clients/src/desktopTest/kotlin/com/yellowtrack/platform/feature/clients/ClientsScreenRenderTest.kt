package com.yellowtrack.platform.feature.clients

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.clients.presentation.component.ClientFormDialog
import com.yellowtrack.platform.feature.clients.presentation.details.ClientDetailsScreen
import com.yellowtrack.platform.feature.clients.presentation.details.preview.ClientDetailsPreviewData
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsScreen
import com.yellowtrack.platform.feature.clients.presentation.list.ClientsUiState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

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
        render("client-details.png", height = 2_200) {
            ClientDetailsScreen(
                uiState = ClientDetailsPreviewData.successState,
                onRetry = {},
                onBack = {},
                onScheduleSession = {},
                onAddProject = {},
                onUpdateProject = { _, _ -> },
                onUpdateClient = {},
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
