package com.yellowtrack.platform.feature.auth

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.feature.auth.presentation.SignInFields
import com.yellowtrack.platform.feature.auth.presentation.SignInMode
import com.yellowtrack.platform.feature.auth.presentation.SignInScreen
import com.yellowtrack.platform.feature.auth.presentation.SignInUiState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the way in, so somebody can look at the first screen the application shows. */
class SignInRenderTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `renders signing in and starting a studio`() {
        render("sign-in.png", SignInUiState(fields = SignInFields(email = "ada@harbourline.test", password = "secret")))

        // The first screen anybody sees, at the width most of them will see it on.
        render(
            "sign-in-phone.png",
            SignInUiState(fields = SignInFields(email = "ada@harbourline.test", password = "secret")),
            width = 780,
        )

        render(
            "reset-enter-code.png",
            SignInUiState(
                mode = SignInMode.EnterCode,
                fields =
                    SignInFields(
                        email = "ada@harbourline.test",
                        code = "XNFAR-JVDPG",
                        password = "a completely new password",
                    ),
                notice = "If that address has an account, a code is on its way. It expires in an hour.",
            ),
        )

        render(
            "sign-up.png",
            SignInUiState(
                mode = SignInMode.SignUp,
                fields =
                    SignInFields(
                        email = "ada@harbourline.test",
                        password = "a long enough password",
                        name = "Ada Okafor",
                        studioName = "Harbourline Photography",
                    ),
                // The browser case, which is the one worth looking at: the warning has to be
                // legible rather than a footnote.
                isHardwareBacked = false,
            ),
        )

        // The address that started this: well-formed, deliverable to nobody, and the only
        // route back into the account being created. Rendered at phone width because that
        // is where a suggestion has least room to sit under the field it belongs to, and
        // where a long address is most likely to wrap away from the question.
        render(
            "sign-up-email-typo-phone.png",
            SignInUiState(
                mode = SignInMode.SignUp,
                fields =
                    SignInFields(
                        email = "ada.okafor@gmail.ocm",
                        password = "a long enough password",
                        name = "Ada Okafor",
                        studioName = "Harbourline Photography",
                    ),
            ),
            width = 780,
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(
        name: String,
        state: SignInUiState,
        width: Int = 1_100,
    ) {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, name)

        val scene =
            ImageComposeScene(width = width, height = 1_700, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = YTTheme.colors.background) {
                        SignInScreen(
                            uiState = state,
                            onEmailChanged = {},
                            onPasswordChanged = {},
                            onNameChanged = {},
                            onStudioNameChanged = {},
                            onCodeChanged = {},
                            onModeChanged = {},
                            onSubmit = {},
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
