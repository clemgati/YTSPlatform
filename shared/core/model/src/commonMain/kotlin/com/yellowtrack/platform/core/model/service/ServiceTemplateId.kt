package com.yellowtrack.platform.core.model.service

import com.yellowtrack.platform.core.common.id.uuidV7
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

/** Identifies a reusable definition of a kind of work the studio sells. */
@Serializable
@JvmInline
value class ServiceTemplateId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): ServiceTemplateId = ServiceTemplateId(uuidV7().toString())
    }
}
