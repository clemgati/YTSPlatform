package com.yellowtrack.platform.core.model.client

import com.yellowtrack.platform.core.model.common.AuditMetadata
import com.yellowtrack.platform.core.model.common.StudioId
import com.yellowtrack.platform.core.model.common.StudioScoped
import com.yellowtrack.platform.core.model.contact.ContactId
import kotlinx.serialization.Serializable

/**
 * That a person is attached to a client account, in a particular role — as a row.
 *
 * [ClientContact] is the same fact assembled for a screen: it carries the whole [Contact]
 * so a caller can render a name without a second lookup. This is what is stored and what
 * travels, and the difference matters for exactly one reason.
 *
 * ADR 0008 decision 5 has child collections reconcile by union on their own ids rather than
 * being replaced along with their parent. A `Client` therefore crosses the wire with no
 * contacts at all, and each attachment crosses as one of these — so two devices that each
 * added a contact to the same client end up with both, instead of whichever saved last
 * silently discarding the other's.
 *
 * It needs no merge machinery to do that. Every row here has its own id, version and
 * `deleted_at`, so two devices adding contacts are writing two different rows, and
 * ordinary row-level synchronisation carries both. The union is a consequence of the shape,
 * not an algorithm.
 */
@Serializable
data class ClientContactLink(
    val id: ClientContactLinkId,
    override val studioId: StudioId,
    val clientId: ClientId,
    val contactId: ContactId,
    val role: ClientContactRole,
    override val audit: AuditMetadata,
) : StudioScoped
