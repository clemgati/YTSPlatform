package com.yellowtrack.platform.core.model.common

import com.yellowtrack.platform.core.common.id.uuidV7
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

/** Identifies a studio — the tenant that owns every other entity. */
@Serializable
@JvmInline
value class StudioId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): StudioId = StudioId(uuidV7().toString())
    }
}
