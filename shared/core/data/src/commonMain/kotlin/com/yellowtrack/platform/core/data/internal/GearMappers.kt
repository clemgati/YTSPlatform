package com.yellowtrack.platform.core.data.internal

import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.gear.GearCategory
import com.yellowtrack.platform.core.model.gear.GearItem
import com.yellowtrack.platform.core.model.gear.GearItemId
import com.yellowtrack.platform.core.model.gear.GearStatus
import com.yellowtrack.platform.core.model.gear.LightSetup
import com.yellowtrack.platform.core.model.gear.LightingRecipe
import com.yellowtrack.platform.core.model.gear.LightingRecipeId
import com.yellowtrack.platform.core.model.gear.PackingEntry
import com.yellowtrack.platform.core.model.gear.PackingEntryId
import com.yellowtrack.platform.core.model.session.SessionId
import kotlinx.datetime.LocalDate
import com.yellowtrack.platform.core.database.Gear_item as GearRow
import com.yellowtrack.platform.core.database.Lighting_recipe as RecipeRow
import com.yellowtrack.platform.core.database.Packing_entry as PackingRow

internal fun GearRow.toDomain(): GearItem =
    GearItem(
        id = GearItemId(id),
        studioId = StudioId(studio_id),
        name = name,
        category = enumOrDefault(category, GearCategory.Other),
        // An unreadable status reads as in service: gear wrongly marked retired drops off
        // the insurance total, which is the one figure nobody wants quietly understated.
        status = enumOrDefault(status, GearStatus.InService),
        serialNumber = serial_number,
        purchasePrice =
            purchase_price_minor?.let { minor ->
                purchase_currency?.let { Money(minor, CurrencyCode(it)) }
            },
        purchasedOn = purchased_on?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        lastServicedAt = last_serviced_at.toInstantOrNull(),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

internal fun PackingRow.toDomain(): PackingEntry =
    PackingEntry(
        id = PackingEntryId(id),
        studioId = StudioId(studio_id),
        sessionId = SessionId(session_id),
        gearItemId = GearItemId(gear_item_id),
        isPacked = is_packed != 0L,
        isReturned = is_returned != 0L,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

internal fun RecipeRow.toDomain(): LightingRecipe =
    LightingRecipe(
        id = LightingRecipeId(id),
        studioId = StudioId(studio_id),
        name = name,
        lights = decodeLights(lights),
        notes = notes,
        audit = auditOf(created_at, updated_at, deleted_at, version),
    )

/**
 * The lights are stored as JSON, on the same reasoning as invoice lines and usage
 * licences: read and written whole with their recipe, never queried into.
 */
internal fun encodeLights(lights: List<LightSetup>): String = ledgerJson.encodeToString(lights)

/** A recipe written by a newer version must not crash an older one. */
internal fun decodeLights(raw: String?): List<LightSetup> =
    raw?.let { runCatching { ledgerJson.decodeFromString<List<LightSetup>>(it) }.getOrNull() }.orEmpty()
