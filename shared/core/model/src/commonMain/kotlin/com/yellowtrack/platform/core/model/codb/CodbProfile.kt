package com.yellowtrack.platform.core.model.codb

import com.yellowtrack.platform.core.common.id.uuidV7
import com.yellowtrack.platform.core.common.money.CurrencyCode
import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

@Serializable
@JvmInline
value class CodbProfileId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): CodbProfileId = CodbProfileId(uuidV7().toString())
    }
}

/**
 * The inputs to a studio's cost of doing business.
 *
 * @param targetAnnualSalary what the photographer intends to *take home*, after business
 *   costs and after personal tax. Stated as take-home on purpose: that is the number a
 *   person can reason about, and grossing it up is the calculator's job.
 *
 * @param billableDaysPerYear how many days a year the studio can actually **sell** — not
 *   how many days it works. This is where most estimates go wrong. A wedding photographer
 *   shooting 25 weddings does not have 25 billable days: each wedding also consumes
 *   culling, editing, album design, and client admin. Counting only the shoot days
 *   inflates this number several times over and produces a day rate far below cost.
 *
 * @param taxRateBasisPoints the photographer's effective rate on business income,
 *   including self-employment or national insurance style contributions where they apply.
 *
 * @param annualOverheadOverride set to state overhead directly instead of summing the
 *   studio's unlinked expenses. Useful before a full year of expenses has been recorded,
 *   which is exactly when a new photographer most needs this number.
 */
@Serializable
data class CodbProfile(
    val id: CodbProfileId,
    override val studioId: StudioId,
    val currency: CurrencyCode,
    val targetAnnualSalary: Money,
    val billableDaysPerYear: Int,
    val taxRateBasisPoints: Int = 0,
    val annualOverheadOverride: Money? = null,
    /** Retained on top of costs and pay — reinvestment, and a buffer against a bad year. */
    val desiredProfitMarginBasisPoints: Int = 0,
    override val audit: AuditMetadata,
) : StudioScoped {
    init {
        require(billableDaysPerYear > 0) { "A studio must have at least one billable day" }
        require(taxRateBasisPoints in 0 until BASIS_POINT_SCALE) {
            "Tax rate must be below 100%, was $taxRateBasisPoints basis points"
        }
        require(desiredProfitMarginBasisPoints >= 0) { "Profit margin cannot be negative" }
    }

    companion object {
        const val BASIS_POINT_SCALE = 10_000
    }
}
