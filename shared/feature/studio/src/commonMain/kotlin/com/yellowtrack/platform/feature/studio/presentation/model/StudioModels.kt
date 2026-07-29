package com.yellowtrack.platform.feature.studio.presentation.model

import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.GearStatus
import com.yellowtrack.platform.core.model.gear.LightRole
import com.yellowtrack.platform.core.model.gear.LightingRecipeId

/** One piece of gear, rendered. */
internal data class GearItemUi(
    val id: GearItemId,
    val name: String,
    val category: GearCategory,
    val status: GearStatus,
    val statusLabel: String,
    /** "SN 04127634" or null — never an invented placeholder. */
    val serialLabel: String?,
    val priceLabel: String?,
    val purchasedLabel: String?,
    val servicedLabel: String?,
    /** Owned, worth money, and impossible to identify to a loss adjuster. */
    val isUninsurable: Boolean,
    val isLongUnserviced: Boolean,
    val notes: String?,
)

internal data class GearGroup(
    val category: GearCategory,
    val label: String,
    val items: List<GearItemUi>,
)

/**
 * What the studio owns, in the terms an insurer and a shoot day care about.
 *
 * [insuredValue] is the sum of what was *paid*, not what replacement would cost — those
 * diverge badly over five years, and the screen says so rather than letting a studio
 * insure a 2019 body for its 2019 price and believe it is covered.
 */
internal data class InventorySummary(
    val groups: List<GearGroup>,
    val itemCount: Int,
    val availableCount: Int,
    val insuredValue: Money,
    val itemsWithoutPrice: Int,
    val uninsurableNames: List<String>,
    val unavailableNames: List<String>,
    val longUnservicedNames: List<String>,
)

internal data class LightSetupUi(
    val role: LightRole,
    val roleLabel: String,
    /** "Profoto B10 through a 3ft octabox" — instrument and modifier read as one phrase. */
    val instrumentLabel: String,
    val settingsLabel: String?,
)

internal data class LightingRecipeItem(
    val id: LightingRecipeId,
    val name: String,
    val lights: List<LightSetupUi>,
    val lightCountLabel: String,
    val notes: String?,
)

// --- Form output --------------------------------------------------------------------

internal data class NewGearItem(
    val name: String,
    val category: GearCategory,
    val status: GearStatus,
    val serialNumber: String?,
    val purchasePrice: String?,
    val purchasedOn: String?,
    val lastServicedOn: String?,
    val notes: String?,
)

internal data class NewLightSetup(
    val role: LightRole,
    val instrument: String,
    val modifier: String?,
    val power: String?,
    val position: String?,
    val distance: String?,
)

internal data class NewLightingRecipe(
    val name: String,
    val lights: List<NewLightSetup>,
    val notes: String?,
)
