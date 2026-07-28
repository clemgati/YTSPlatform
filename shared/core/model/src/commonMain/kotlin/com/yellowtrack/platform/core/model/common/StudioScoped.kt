package com.yellowtrack.platform.core.model.common

/**
 * Implemented by every entity that belongs to a studio.
 *
 * Tenant scope is present from the first table rather than added when a second
 * photographer arrives: retrofitting a tenant column onto a populated multi-user
 * database has no safe migration path, and this is the column Postgres Row Level
 * Security will key on.
 */
interface StudioScoped {
    val studioId: StudioId
    val audit: AuditMetadata
}
