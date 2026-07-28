package com.yellowtrack.platform.core.model.session

import com.yellowtrack.platform.core.common.id.uuidV7
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

/** Identifies a scheduled block of work inside a project. */
@Serializable
@JvmInline
value class SessionId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): SessionId = SessionId(uuidV7().toString())
    }
}
