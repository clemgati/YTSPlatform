package com.yellowtrack.platform.core.model.gear

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class GearItemId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): GearItemId = GearItemId(uuidV7().toString())
    }
}

@Serializable
enum class GearCategory {
    Camera,
    Lens,
    Lighting,

    /** Softboxes, umbrellas, grids — what shapes the light rather than makes it. */
    Modifier,

    Audio,

    /** Tripods, stands, gimbals. */
    Support,

    /** Cards, drives, readers. */
    Storage,

    Other,
}

@Serializable
enum class GearStatus {
    InService,

    /** Away being fixed. Not available to pack, and not lost. */
    InRepair,

    /** Sold or written off. Kept for the record and out of the insurance total. */
    Retired,

    /** Missing. Distinct from retired: this one may still be claimed for. */
    Lost,
    ;

    val isAvailable: Boolean get() = this == InService

    /** Whether it still belongs on an insurance schedule. */
    val isOwned: Boolean get() = this == InService || this == InRepair || this == Lost
}

/**
 * A piece of equipment the studio owns.
 *
 * @param serialNumber the field that decides whether a stolen body ever comes back, and
 *   whether an insurer pays. Nullable because plenty of gear has none — a reflector, a
 *   bag of clamps — and demanding one would push a studio into inventing them.
 * @param purchasePrice what it cost, which is what an insurance schedule is built from and
 *   what a replacement will actually run to. Held rather than derived: the price paid is a
 *   historical fact and does not change when the model does.
 */
@Serializable
data class GearItem(
    val id: GearItemId,
    override val studioId: StudioId,
    val name: String,
    val category: GearCategory = GearCategory.Other,
    val status: GearStatus = GearStatus.InService,
    val serialNumber: String? = null,
    val purchasePrice: Money? = null,
    val purchasedOn: LocalDate? = null,
    val lastServicedAt: Instant? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    /** Gear that could be claimed for but could not be identified to a loss adjuster. */
    val isUninsurable: Boolean
        get() = status.isOwned && purchasePrice != null && serialNumber.isNullOrBlank()
}
