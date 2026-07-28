package com.yellowtrack.platform.core.model.common

import com.yellowtrack.platform.core.common.time.AppClock
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The audit and synchronisation columns carried by every persisted entity.
 *
 * See `docs/adr/0006-sync-ready-multi-tenant-schema.md`. These fields exist from the
 * first table onward even though synchronisation is not yet implemented, because adding
 * them to a populated multi-device database later has no safe migration path.
 *
 * @param deletedAt soft-delete tombstone. A hard delete cannot propagate to a device that
 *   was offline when it happened, so rows are marked rather than removed.
 * @param version optimistic-concurrency counter, incremented on every local mutation.
 */
@Serializable
data class AuditMetadata(
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val version: Int = 1,
) {
    val isDeleted: Boolean get() = deletedAt != null

    /** Marks a local mutation: bumps [updatedAt] and [version]. */
    fun touched(now: Instant): AuditMetadata = copy(updatedAt = now, version = version + 1)

    /** Marks the entity deleted without removing it, so the tombstone can be synchronised. */
    fun deleted(now: Instant): AuditMetadata = copy(updatedAt = now, deletedAt = now, version = version + 1)

    companion object {
        fun createdAt(now: Instant): AuditMetadata = AuditMetadata(createdAt = now, updatedAt = now)

        fun createdNow(clock: AppClock = AppClock.System): AuditMetadata = createdAt(clock.now())
    }
}
