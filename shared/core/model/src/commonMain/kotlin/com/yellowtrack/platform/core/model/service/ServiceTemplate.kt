package com.yellowtrack.platform.core.model.service

import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import kotlinx.serialization.Serializable

/**
 * A reusable definition of a kind of work, supplying the defaults for a new project.
 *
 * This is how one schema serves weddings, brand video, real estate, and headshots at
 * once: the business lines differ in the values here, not in code.
 *
 * @param defaultSessionCount a wedding template defaults to two — the engagement shoot
 *   and the wedding day. A commercial template may default to three: scout, shoot, pickup.
 * @param defaultTurnaroundDays what the contract promises. Missing a promised turnaround
 *   is the most common source of client friction in this business.
 * @param defaultRevisionRounds the primary defence against scope creep on video work.
 *   Unlimited revisions turn a profitable job into a loss.
 */
@Serializable
data class ServiceTemplate(
    val id: ServiceTemplateId,
    override val studioId: StudioId,
    val name: String,
    val serviceLine: ServiceLine,
    val defaultSessionDurationMinutes: Int,
    val defaultSessionCount: Int = 1,
    val basePrice: Money? = null,
    val defaultDeliverableCount: Int? = null,
    val defaultTurnaroundDays: Int? = null,
    val defaultRevisionRounds: Int? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped
