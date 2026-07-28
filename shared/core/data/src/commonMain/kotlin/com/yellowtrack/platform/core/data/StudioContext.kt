package com.yellowtrack.platform.core.data

import com.yellowtrack.platform.core.model.common.StudioId

/**
 * Supplies the studio whose data the application is currently working with.
 *
 * Every query is scoped by this. Today there is exactly one studio and it is a constant;
 * when accounts arrive, this becomes the signed-in user's studio and the same queries
 * keep working. See `docs/adr/0006-sync-ready-multi-tenant-schema.md`.
 */
interface StudioContext {
    val studioId: StudioId
}

/**
 * The single local studio used before accounts exist.
 *
 * A fixed identifier rather than a generated one, so that a database created by an
 * earlier run is still readable after a restart.
 */
class LocalStudioContext : StudioContext {
    override val studioId: StudioId = LOCAL_STUDIO_ID

    companion object {
        val LOCAL_STUDIO_ID: StudioId = StudioId("00000000-0000-7000-8000-000000000001")
    }
}
