package com.yellowtrack.platform.feature.studio.presentation.mapper

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.common.money.formatted
import com.yellowtrack.platform.core.common.time.DateFormats
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearStatus
import com.yellowtrack.platform.core.model.gear.LightRole
import com.yellowtrack.platform.core.model.gear.LightSetup
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.media.StorageKind
import com.yellowtrack.platform.core.model.media.StorageVolume
import com.yellowtrack.platform.core.model.media.StorageVolumeId
import com.yellowtrack.platform.core.model.media.VolumeStatus
import com.yellowtrack.platform.feature.studio.presentation.model.GearGroup
import com.yellowtrack.platform.feature.studio.presentation.model.GearItemUi
import com.yellowtrack.platform.feature.studio.presentation.model.InventorySummary
import com.yellowtrack.platform.feature.studio.presentation.model.LightSetupUi
import com.yellowtrack.platform.feature.studio.presentation.model.LightingRecipeItem
import com.yellowtrack.platform.feature.studio.presentation.model.VolumeItem
import com.yellowtrack.platform.feature.studio.presentation.model.VolumeRegister
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * How long a service record has to be before it is worth mentioning.
 *
 * A year is the interval manufacturers' service programmes are sold on, and it is only
 * ever applied to gear the studio has *already* recorded a service for — see
 * [isLongUnserviced]. Applying it to everything would tell a studio its reflector
 * is overdue a shutter count, and a list that is wrong about half its rows gets ignored.
 */
internal val SERVICE_INTERVAL = 365.days

internal fun buildInventory(
    gear: List<GearItem>,
    now: Instant,
    zone: TimeZone,
    currency: CurrencyCode,
): InventorySummary {
    val items = gear.map { it.toUi(now, zone) }
    val owned = gear.filter { it.status.isOwned }

    val groups =
        GearCategory.entries.mapNotNull { category ->
            val inCategory = items.filter { it.category == category }

            if (inCategory.isEmpty()) {
                null
            } else {
                GearGroup(
                    category = category,
                    label = category.label,
                    items = inCategory.sortedBy { it.name.lowercase() },
                )
            }
        }

    return InventorySummary(
        groups = groups,
        itemCount = items.size,
        availableCount = gear.count { it.status.isAvailable },
        insuredValue =
            owned
                .mapNotNull { it.purchasePrice }
                .fold(Money.zero(currency)) { total, price -> total + price },
        itemsWithoutPrice = owned.count { it.purchasePrice == null },
        uninsurableNames = gear.filter { it.isUninsurable }.map { it.name },
        unavailableNames = gear.filter { !it.status.isAvailable && it.status.isOwned }.map { it.name },
        longUnservicedNames = gear.filter { it.isLongUnserviced(now) }.map { it.name },
    )
}

/**
 * Whether a service record has gone stale.
 *
 * Only gear that has been serviced at least once is considered: a studio that services its
 * bodies has told us it services them, and a reflector that has never been serviced is not
 * overdue anything.
 */
internal fun GearItem.isLongUnserviced(now: Instant): Boolean {
    val serviced = lastServicedAt ?: return false

    return status.isOwned && now - serviced > SERVICE_INTERVAL
}

private fun GearItem.toUi(
    now: Instant,
    zone: TimeZone,
): GearItemUi =
    GearItemUi(
        id = id,
        name = name,
        category = category,
        status = status,
        statusLabel = status.label,
        serialLabel = serialNumber?.takeIf { it.isNotBlank() }?.let { "SN $it" },
        priceLabel = purchasePrice?.formatted(),
        purchasedLabel = purchasedOn?.let { "Bought $it" },
        servicedLabel = lastServicedAt?.let { "Serviced ${DateFormats.fullDate(it, zone)}" },
        isUninsurable = isUninsurable,
        isLongUnserviced = isLongUnserviced(now),
        notes = notes,
    )

internal fun LightingRecipe.toItem(): LightingRecipeItem =
    LightingRecipeItem(
        id = id,
        name = name,
        lights = lights.map { it.toUi() },
        lightCountLabel =
            when (lightCount) {
                0 -> "No lights written down yet"
                1 -> "1 light"
                else -> "$lightCount lights"
            },
        notes = notes,
    )

private fun LightSetup.toUi(): LightSetupUi =
    LightSetupUi(
        role = role,
        roleLabel = role.label,
        instrumentLabel = modifier?.takeIf { it.isNotBlank() }?.let { "$instrument through $it" } ?: instrument,
        // Power, distance and position are what actually gets dialled in, and they are only
        // useful together — a power with no distance is a number nobody can reproduce.
        settingsLabel =
            listOfNotNull(
                power?.takeIf { it.isNotBlank() },
                distance?.takeIf { it.isNotBlank() },
                position?.takeIf { it.isNotBlank() },
            ).takeIf { it.isNotEmpty() }
                ?.joinToString(" · "),
    )

internal val GearCategory.label: String
    get() =
        when (this) {
            GearCategory.Camera -> "Cameras"
            GearCategory.Lens -> "Lenses"
            GearCategory.Lighting -> "Lighting"
            GearCategory.Modifier -> "Modifiers"
            GearCategory.Audio -> "Audio"
            GearCategory.Support -> "Support"
            GearCategory.Storage -> "Storage"
            GearCategory.Other -> "Everything else"
        }

internal val GearStatus.label: String
    get() =
        when (this) {
            GearStatus.InService -> "In service"
            GearStatus.InRepair -> "Being repaired"
            GearStatus.Retired -> "Retired"
            GearStatus.Lost -> "Lost"
        }

internal val LightRole.label: String
    get() =
        when (this) {
            LightRole.Key -> "Key"
            LightRole.Fill -> "Fill"
            LightRole.Rim -> "Rim"
            LightRole.Background -> "Background"
            LightRole.Bounce -> "Bounce"
        }

/**
 * The register, with what is at stake on each drive.
 *
 * A drive is worth listing whatever its state; a *failed* drive is worth acting on, and
 * the count of shoots sitting on it is the reason to act rather than a statistic.
 */
internal fun buildRegister(
    volumes: List<StorageVolume>,
    copyCounts: Map<StorageVolumeId, Int>,
    now: Instant,
    zone: TimeZone,
): VolumeRegister {
    val items =
        volumes.map { volume ->
            VolumeItem(
                id = volume.id,
                label = volume.label,
                kindLabel = volume.kind.label,
                status = volume.status,
                statusLabel = volume.status.label,
                whereLabel = if (volume.isAwayFromStudio) "Away from the studio" else "In the studio",
                // Not said of a dead drive: whether anyone opened it lately is not the
                // advice, and it would read as one more thing to go and do.
                checkedLabel =
                    volume.lastCheckedAt.let { checked ->
                        when {
                            !volume.isDependable -> null
                            checked != null -> "Last read ${DateFormats.fullDate(checked, zone)}"
                            else -> "Never checked"
                        }
                    },
                copyCount = copyCounts[volume.id] ?: 0,
                isDependable = volume.isDependable,
                notes = volume.notes,
            )
        }

    return VolumeRegister(
        volumes = items.sortedWith(compareBy({ it.isDependable }, { it.label.lowercase() })),
        failedCount = items.count { !it.isDependable },
        copiesAtRisk = items.filter { !it.isDependable }.sumOf { it.copyCount },
        // A drive nobody has ever opened is the one that fails silently and is discovered
        // on the day it is needed.
        neverCheckedCount = items.count { it.isDependable && it.checkedLabel == "Never checked" },
    )
}

internal val StorageKind.label: String
    get() =
        when (this) {
            StorageKind.CameraCard -> "Camera card"
            StorageKind.Computer -> "Computer"
            StorageKind.ExternalDrive -> "External drive"
            StorageKind.Nas -> "NAS"
            StorageKind.Cloud -> "Cloud"
            StorageKind.OffsiteDrive -> "Offsite drive"
        }

internal val VolumeStatus.label: String
    get() =
        when (this) {
            VolumeStatus.InUse -> "In use"
            VolumeStatus.Failed -> "Failed"
            VolumeStatus.Retired -> "Retired"
            VolumeStatus.Lost -> "Lost"
        }
