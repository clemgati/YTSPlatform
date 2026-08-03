package com.yellowtrack.platform.feature.studio

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.data.LocalStudioContext
import com.yellowtrack.platform.core.designsystem.theme.YTTheme
import com.yellowtrack.platform.core.designsystem.theme.YellowTrackTheme
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.GearStatus
import com.yellowtrack.platform.core.model.gear.LightRole
import com.yellowtrack.platform.core.model.gear.LightSetup
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
import com.yellowtrack.platform.core.testing.TestAppClock
import com.yellowtrack.platform.core.ui.state.UiState
import com.yellowtrack.platform.feature.studio.presentation.StudioContent
import com.yellowtrack.platform.feature.studio.presentation.StudioScreen
import com.yellowtrack.platform.feature.studio.presentation.StudioUiState
import com.yellowtrack.platform.feature.studio.presentation.mapper.buildInventory
import com.yellowtrack.platform.feature.studio.presentation.mapper.buildRegister
import com.yellowtrack.platform.feature.studio.presentation.mapper.toItem
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/**
 * Renders the Studio tab off-screen so a person can look at it.
 *
 * The sample is deliberately imperfect — a body with no serial, a light with no price, a
 * lens overdue a service — because the screen's whole job is to surface those, and a
 * tidy sample would prove nothing.
 */
class StudioScreenRenderTest {
    private val usd = CurrencyCode.USD
    private val now = TestAppClock.DEFAULT_NOW
    private var volumeSample: List<StorageVolume> = emptyList()

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `renders the studio to a png`() {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, "studio.png")

        val scene =
            ImageComposeScene(width = WIDE, height = 3_400, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = YTTheme.colors.background,
                    ) {
                        StudioScreen(
                            uiState = StudioUiState(content = UiState.Success(sampleContent())),
                            onRetry = {},
                            onSaveGear = { _, _ -> },
                            onMarkServiced = {},
                            onDeleteGear = {},
                            onSaveRecipe = { _, _ -> },
                            onDeleteRecipe = {},
                            onSaveVolume = { _, _ -> },
                            onMarkVolumeChecked = {},
                            onSetVolumeStatus = { _, _ -> },
                            onDeleteVolume = {},
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

    /**
     * The same screen at the width of a phone.
     *
     * Rendered at 640dp everything fitted and nothing looked wrong. On a real phone the row
     * actions took the width they needed and left the item name about eighty pixels, so
     * "Sony A6700m3" arrived over three lines and the serial number underneath it was one
     * character wide. Only a narrow render catches that, so there is one now.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `renders the studio at the width of a phone`() {
        val outputDir = File(System.getProperty("yellowtrack.render.dir") ?: "build/render")
        outputDir.mkdirs()
        val target = File(outputDir, "studio-phone.png")

        val scene =
            ImageComposeScene(width = NARROW, height = 5_600, density = Density(2f)) {
                YellowTrackTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = YTTheme.colors.background,
                    ) {
                        StudioScreen(
                            uiState = StudioUiState(content = UiState.Success(sampleContent())),
                            onRetry = {},
                            onSaveGear = { _, _ -> },
                            onMarkServiced = {},
                            onDeleteGear = {},
                            onSaveRecipe = { _, _ -> },
                            onDeleteRecipe = {},
                            onSaveVolume = { _, _ -> },
                            onMarkVolumeChecked = {},
                            onSetVolumeStatus = { _, _ -> },
                            onDeleteVolume = {},
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

    private companion object {
        const val WIDE = 1_280

        /** A 390pt phone at 2x, which is what the screenshots that found this came from. */
        const val NARROW = 780
    }

    private fun sampleContent(): StudioContent {
        val gear =
            listOf(
                gear("Canon R5 body", GearCategory.Camera, serial = "042176003", price = 389_900L),
                gear("Canon R6 body", GearCategory.Camera, serial = null, price = 249_900L),
                gear("24-70mm f/2.8", GearCategory.Lens, serial = "6620013", price = 210_000L, servicedDaysAgo = 430),
                gear("85mm f/1.4", GearCategory.Lens, serial = "9910442", price = 159_900L),
                gear(
                    "Profoto B10",
                    GearCategory.Lighting,
                    serial = "PB10-7741",
                    price = 189_500L,
                    status = GearStatus.InRepair,
                ),
                gear("3ft octabox", GearCategory.Modifier, serial = null, price = null),
                gear("Manfrotto 1004BAC stand", GearCategory.Support, serial = null, price = null),
            )

        return StudioContent(
            inventory = buildInventory(gear, now, TimeZone.UTC, usd),
            recipes =
                listOf(
                    recipe(
                        "Clamshell headshot",
                        listOf(
                            LightSetup(
                                role = LightRole.Key,
                                instrument = "Profoto B10",
                                modifier = "3ft octabox",
                                power = "1/4",
                                position = "Straight on, 45° above eye line",
                                distance = "1.2m",
                            ),
                            LightSetup(
                                role = LightRole.Fill,
                                instrument = "Silver reflector",
                                power = null,
                                position = "Held at chest height",
                            ),
                        ),
                        notes = "Drop the fill a stop for men.",
                    ),
                    recipe(
                        "Three-light corporate",
                        listOf(
                            LightSetup(LightRole.Key, "Profoto B10", "3ft octabox", "1/2", "Camera left, 45°", "1.5m"),
                            LightSetup(
                                LightRole.Rim,
                                "Godox AD200",
                                "Strip box with grid",
                                "1/8",
                                "Behind, camera right",
                            ),
                            LightSetup(
                                LightRole.Background,
                                "Godox AD200",
                                "Blue gel",
                                "1/16",
                                "On the floor, aimed up",
                            ),
                        ),
                        notes = null,
                    ),
                ),
            register =
                buildRegister(
                    volumes =
                        listOf(
                            storageVolume("Studio iMac", StorageKind.Computer),
                            // The row the register exists for.
                            storageVolume("Red Samsung T7", StorageKind.ExternalDrive, VolumeStatus.Failed),
                            storageVolume("Backblaze", StorageKind.Cloud, checkedDaysAgo = 12),
                            storageVolume("Drive at Mum's", StorageKind.OffsiteDrive, checkedDaysAgo = 200),
                        ).also { volumeSample = it },
                    copyCounts =
                        mapOf(
                            volumeSample[0].id to 14,
                            volumeSample[1].id to 6,
                            volumeSample[2].id to 14,
                            volumeSample[3].id to 9,
                        ),
                    now = now,
                    zone = TimeZone.UTC,
                ),
            today = LocalDate(2026, 6, 12),
            currency = usd,
        )
    }

    private fun gear(
        name: String,
        category: GearCategory,
        serial: String?,
        price: Long?,
        status: GearStatus = GearStatus.InService,
        servicedDaysAgo: Long? = null,
    ) = GearItem(
        id = GearItemId.new(),
        studioId = LocalStudioContext.LOCAL_STUDIO_ID,
        name = name,
        category = category,
        status = status,
        serialNumber = serial,
        purchasePrice = price?.let { Money(it, usd) },
        lastServicedAt = servicedDaysAgo?.let { now - it.days },
        audit = AuditMetadata.createdAt(now),
    )

    private fun recipe(
        name: String,
        lights: List<LightSetup>,
        notes: String?,
    ) = LightingRecipe(
        id = LightingRecipeId.new(),
        studioId = LocalStudioContext.LOCAL_STUDIO_ID,
        name = name,
        lights = lights,
        notes = notes,
        audit = AuditMetadata.createdAt(now),
    ).toItem()

    private fun storageVolume(
        label: String,
        kind: StorageKind,
        status: VolumeStatus = VolumeStatus.InUse,
        checkedDaysAgo: Long? = null,
    ) = StorageVolume(
        id = StorageVolumeId.new(),
        studioId = LocalStudioContext.LOCAL_STUDIO_ID,
        label = label,
        kind = kind,
        status = status,
        lastCheckedAt = checkedDaysAgo?.let { now - it.days },
        audit = AuditMetadata.createdAt(now),
    )
}
