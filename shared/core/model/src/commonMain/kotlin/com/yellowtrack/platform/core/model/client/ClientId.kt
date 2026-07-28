package com.yellowtrack.platform.core.model.client

import com.yellowtrack.platform.core.common.id.uuidV7
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

/** Identifies a client account — an individual, a couple, or a company. */
@Serializable
@JvmInline
value class ClientId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): ClientId = ClientId(uuidV7().toString())
    }
}
