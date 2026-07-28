package com.yellowtrack.platform.core.model.project

import com.yellowtrack.platform.core.common.id.uuidV7
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

/** Identifies a booking — the commercial container for one or more sessions. */
@Serializable
@JvmInline
value class ProjectId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): ProjectId = ProjectId(uuidV7().toString())
    }
}
