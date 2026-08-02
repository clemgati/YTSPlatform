package com.yellowtrack.platform.core.model.client

import com.yellowtrack.platform.core.common.id.uuidV7
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi

/** Identifies one person's attachment to one client account. */
@Serializable
@JvmInline
value class ClientContactLinkId(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun new(): ClientContactLinkId = ClientContactLinkId(uuidV7().toString())
    }
}
