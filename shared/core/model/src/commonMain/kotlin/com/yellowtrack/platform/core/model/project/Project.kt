package com.yellowtrack.platform.core.model.project

import com.yellowtrack.platform.core.common.money.Money
import com.yellowtrack.platform.core.model.client.ClientId
import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.service.ServiceLine
import com.yellowtrack.platform.core.model.service.ServiceTemplateId
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A booking: one client, one contract, one set of invoices, one profit-and-loss.
 *
 * A project is not a session. A wedding is one project containing an engagement shoot
 * *and* the wedding day; a commercial job is one project containing a scout day, a shoot
 * day, and a pickup day. Treating the session as the booking unit makes "what did that
 * wedding actually earn me?" a question with no answer.
 *
 * @param contractValue the agreed total. Kept on the project rather than derived from
 *   invoices so that a booked-but-not-yet-invoiced project still reports pipeline value.
 */
@Serializable
data class Project(
    val id: ProjectId,
    override val studioId: StudioId,
    val clientId: ClientId,
    val name: String,
    val serviceLine: ServiceLine,
    val status: ProjectStatus,
    val serviceTemplateId: ServiceTemplateId? = null,
    val contractValue: Money? = null,
    val enquiredAt: Instant? = null,
    val bookedAt: Instant? = null,
    val notes: String? = null,
    override val audit: AuditMetadata,
) : StudioScoped
