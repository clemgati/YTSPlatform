package com.yellowtrack.platform.core.model.expense

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.project.ProjectId
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class MileageId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): MileageId = MileageId(uuidV7().toString())
    }
}

@Serializable
enum class DistanceUnit {
    Miles,
    Kilometres,
}

/**
 * A journey made for work.
 *
 * Tracked separately from [Expense] because it is not money spent — it is a deduction
 * claimed against a per-distance rate. Photographers drive constantly, to scouts, shoots,
 * client meetings, and album deliveries, and almost universally fail to log any of it.
 * It is the most commonly forfeited deduction in the business, and it is pure recovered
 * money for the price of recording an odometer reading.
 *
 * @param ratePerUnit the deduction rate at the time of travel. Stored per record rather
 *   than read from a setting, because published rates change annually and a journey must
 *   keep the rate that applied when it happened.
 */
@Serializable
data class Mileage(
    val id: MileageId,
    override val studioId: StudioId,
    val travelledOn: LocalDate,
    val distance: Double,
    val unit: DistanceUnit,
    val ratePerUnit: Money,
    val projectId: ProjectId? = null,
    val purpose: String? = null,
    val fromLocation: String? = null,
    val toLocation: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped {
    init {
        require(distance >= 0) { "Distance cannot be negative" }
    }

    val deductibleAmount: Money get() = ratePerUnit.timesQuantity(distance)

    val isJobCost: Boolean get() = projectId != null
}
